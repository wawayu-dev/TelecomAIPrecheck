package cn.telecom.precheck.precheck;

import cn.telecom.precheck.context.ContextUnavailableException;
import cn.telecom.precheck.context.TicketNotFoundException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

@Service
@Profile("mysql")
public class AsyncPrecheckService {
    private static final Logger LOG = LoggerFactory.getLogger(AsyncPrecheckService.class);
    private static final Set<String> RETRYABLE = Set.of("MODEL_TIMEOUT", "MODEL_ERROR", "INVALID_OUTPUT",
            "CONTEXT_UNAVAILABLE", "INTERRUPTED");
    private final PrecheckStore store;
    private final PrecheckMapper mapper;
    private final PrecheckEngine engine;
    private final PrecheckModel model;
    private final Executor executor;
    private final PrecheckTaskProperties taskProperties;
    private final TicketEventProperties eventProperties;

    public AsyncPrecheckService(PrecheckStore store, PrecheckMapper mapper, PrecheckEngine engine,
            PrecheckModel model, @Qualifier("precheckTaskExecutor") Executor executor,
            PrecheckTaskProperties taskProperties, TicketEventProperties eventProperties) {
        this.store = store; this.mapper = mapper; this.engine = engine; this.model = model;
        this.executor = executor; this.taskProperties = taskProperties; this.eventProperties = eventProperties;
    }

    public record Receipt(String runId, String taskStatus, boolean created, boolean accepted) {}

    public Receipt accept(TicketQueueEvent event, String traceId) {
        var queued = store.enqueue(UUID.randomUUID().toString(), eventProperties.source(), event.eventId(),
                event.ticketId(), event.ticketVersion(), model.modelId(), traceId, taskProperties.maxAttempts());
        if (queued.created()) dispatch(queued.run().id());
        return new Receipt(queued.run().id(), queued.run().taskStatus(), queued.created(), true);
    }

    public void dispatch(String id) {
        try { executor.execute(() -> execute(id)); }
        catch (TaskRejectedException rejected) {
            // 任务已持久化；扫描器会在执行器恢复容量后重新投递。
            LOG.warn("Precheck executor saturated; persisted task will be resumed id={}", id);
        }
    }

    void execute(String id) {
        if (!store.claim(id)) return;
        var run = mapper.find(id);
        try {
            var context = engine.load(run.ticketId());
            store.saveTaskSnapshot(id, run.attemptCount(), engine.encode(context));
            var outcome = engine.analyze(run.ticketId(), context);
            if (outcome.failureCode() != null && shouldRetry(run, outcome.failureCode())) {
                store.retry(id, run.attemptCount(), outcome.failureCode(), outcome.durationMs(), taskProperties.retryDelaySeconds());
                return;
            }
            store.finishTask(id, run.attemptCount(), outcome.failureCode() == null ? "SUCCESS" : "FAILED", outcome.actualModel(),
                    outcome.durationMs(), outcome.failureCode(), outcome.resultJson(), outcome.findingsJson());
        } catch (StaleTaskAttemptException stale) {
            // 恢复器已经回收本次执行租约；旧线程不得覆盖后续尝试。
        } catch (ContextUnavailableException unavailable) {
            failOrRetry(run, "CONTEXT_UNAVAILABLE", 0);
        } catch (TicketNotFoundException missing) {
            store.finishTask(id, run.attemptCount(), "FAILED", null, 0, "TICKET_NOT_FOUND", null, null);
        } catch (Exception unexpected) {
            failOrRetry(run, "MODEL_ERROR", 0);
        }
    }

    private boolean shouldRetry(PrecheckRun run, String failureCode) {
        return RETRYABLE.contains(failureCode) && run.attemptCount() < run.maxAttempts();
    }
    private void failOrRetry(PrecheckRun run, String code, long durationMs) {
        if (shouldRetry(run, code)) store.retry(run.id(), run.attemptCount(), code, durationMs, taskProperties.retryDelaySeconds());
        else store.finishTask(run.id(), run.attemptCount(), "FAILED", null, durationMs, code, null, null);
    }
}
