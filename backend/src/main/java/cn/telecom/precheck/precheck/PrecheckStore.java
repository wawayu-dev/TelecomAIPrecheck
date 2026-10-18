package cn.telecom.precheck.precheck;

import cn.telecom.precheck.review.AuditMapper;
import cn.telecom.precheck.security.CurrentActor;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Profile("mysql")
public class PrecheckStore {
    private final PrecheckMapper mapper;
    private final AuditMapper audits;
    public PrecheckStore(PrecheckMapper mapper, AuditMapper audits) { this.mapper = mapper; this.audits=audits; }
    @Transactional
    public void begin(String id, String ticketId, String model, String traceId, String contextJson) {
        String actor=CurrentActor.id();
        mapper.insert(id, ticketId, PromptCatalog.VERSION, model, traceId);
        mapper.snapshot(id, contextJson);
        append(id,actor,"PRECHECK_STARTED",null,"COMMITTED",traceId);
    }
    public record EnqueueResult(PrecheckRun run, boolean created) {}
    @Transactional
    public EnqueueResult enqueue(String id, String source, String eventId, String ticketId, long ticketVersion,
                                 String model, String traceId, int maxAttempts) {
        var sameEvent = mapper.findEventId(source, eventId);
        if (sameEvent != null) {
            if (!sameEvent.ticketId().equals(ticketId) || !sameEvent.ticketVersion().equals(ticketVersion))
                throw new EventConflictException();
            return new EnqueueResult(sameEvent, false);
        }
        var existing = mapper.findEventVersion(source, ticketId, ticketVersion);
        if (existing != null) return new EnqueueResult(existing, false);
        if (mapper.insertEvent(id, ticketId, PromptCatalog.VERSION, model, traceId, source, eventId,
                ticketVersion, maxAttempts) != 1) {
            var racedEvent = mapper.findEventId(source, eventId);
            if (racedEvent != null) {
                if (!racedEvent.ticketId().equals(ticketId) || !racedEvent.ticketVersion().equals(ticketVersion))
                    throw new EventConflictException();
                return new EnqueueResult(racedEvent, false);
            }
            var racedVersion = mapper.findEventVersion(source, ticketId, ticketVersion);
            if (racedVersion != null) return new EnqueueResult(racedVersion, false);
            throw new IllegalStateException("Idempotent event insert failed");
        }
        mapper.snapshot(id, "{}");
        mapper.supersedeOlder(source, ticketId, ticketVersion);
        append(id, "system:host-event", "PRECHECK_QUEUED", null, "ACCEPTED", traceId);
        return new EnqueueResult(mapper.find(id), true);
    }
    @Transactional
    public boolean claim(String id) {
        if (mapper.claimTask(id) != 1) return false;
        append(id, "system:precheck-worker", "PRECHECK_TASK_STARTED", "ANALYZING", "RUNNING", mapper.find(id).traceId());
        return true;
    }
    @Transactional
    public void saveTaskSnapshot(String id, int attempt, String contextJson) {
        if (mapper.updateTaskSnapshot(id, attempt, contextJson) != 1)
            throw new StaleTaskAttemptException();
    }
    @Transactional
    public void finishTask(String id, int attempt, String callStatus, String actualModel, long durationMs,
                           String failureCode, String resultJson, String findingsJson) {
        String taskStatus = failureCode == null ? "COMPLETED" : "FAILED";
        if (mapper.finishTask(id, callStatus, taskStatus, actualModel, durationMs, failureCode,
                resultJson, findingsJson, attempt) == 1) {
            append(id, "system:precheck-worker", "PRECHECK_" + callStatus, "ANALYZING", callStatus,
                    mapper.find(id).traceId());
        }
    }
    @Transactional
    public void retry(String id, int attempt, String failureCode, long durationMs, int delaySeconds) {
        if (mapper.scheduleRetry(id, failureCode, durationMs, delaySeconds, attempt) == 1)
            append(id, "system:precheck-worker", "PRECHECK_RETRY_SCHEDULED", "ANALYZING", "RETRY_WAIT",
                    mapper.find(id).traceId());
    }
    @Transactional
    public boolean recoverTask(String id) {
        if (mapper.recoverTaskForRetry(id) == 1) {
            append(id, "system:recovery", "PRECHECK_TASK_RECOVERED", "ANALYZING", "RETRY_WAIT",
                    mapper.find(id).traceId());
            return true;
        }
        if (mapper.failInterruptedTask(id) == 1) {
            append(id, "system:recovery", "PRECHECK_INTERRUPTED", "ANALYZING", "FAILED",
                    mapper.find(id).traceId());
        }
        return false;
    }
    @Transactional
    public void finish(String id, String callStatus, String actualModel, long durationMs,
                       String failureCode, String resultJson, String findingsJson) {
        if(mapper.finish(id, callStatus, actualModel, durationMs, failureCode, resultJson, findingsJson)==1) {
            append(id,"system:model","PRECHECK_"+callStatus,"ANALYZING",callStatus,mapper.find(id).traceId());
        }
    }
    @Transactional
    public void recover(String id) {
        if(mapper.recoverInterrupted(id)==1)
            append(id,"system:recovery","PRECHECK_INTERRUPTED","ANALYZING","FAILED",UUID.randomUUID().toString());
    }
    private void append(String id,String actor,String action,String from,String outcome,String trace) {
        if(audits.append(id,actor,action,from,outcome,trace)!=1) throw new IllegalStateException("Audit write failed");
    }
}
