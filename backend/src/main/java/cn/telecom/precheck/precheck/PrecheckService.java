package cn.telecom.precheck.precheck;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("mysql")
public class PrecheckService {
    private final PrecheckEngine engine;
    private final PrecheckModel model;
    private final PrecheckStore store;
    private final PrecheckMapper mapper;
    private final ObjectMapper json;
    public PrecheckService(PrecheckEngine engine, PrecheckModel model,
            PrecheckStore store, PrecheckMapper mapper, ObjectMapper json) {
        this.engine = engine; this.model = model; this.store = store; this.mapper = mapper; this.json = json;
    }
    public record View(PrecheckRun run, com.fasterxml.jackson.databind.JsonNode result,
                       com.fasterxml.jackson.databind.JsonNode findings, com.fasterxml.jackson.databind.JsonNode evidenceSnapshot) {}
    @PreAuthorize("hasRole('OPERATOR')")
    public View start(String ticketId, String traceId) {
        // 工单整体不存在/宿主失败时不调用模型；已有宿主工单始终保持只读。
        var context = engine.load(ticketId);
        String id = UUID.randomUUID().toString();
        store.begin(id, ticketId, model.modelId(), traceId, engine.encode(context));
        var outcome = engine.analyze(ticketId, context);
        store.finish(id, outcome.failureCode() == null ? "SUCCESS" : "FAILED", outcome.actualModel(),
                outcome.durationMs(), outcome.failureCode(), outcome.resultJson(), outcome.findingsJson());
        return get(id);
    }
    @PreAuthorize("hasAnyRole('OPERATOR','REVIEWER','AUDITOR')")
    public View get(String id) {
        var row = mapper.find(id);
        if (row == null) throw new PrecheckNotFoundException();
        return new View(row, decode(row.resultJson()), decode(row.findingsJson()), decode(mapper.snapshotJson(id)));
    }
    @PreAuthorize("hasAnyRole('OPERATOR','REVIEWER','AUDITOR')")
    public List<View> history(String ticketId, int limit, int offset) {
        return mapper.history(ticketId, limit, offset).stream().map(row ->
                new View(row, decode(row.resultJson()), decode(row.findingsJson()), null)).toList();
    }
    @PreAuthorize("hasAnyRole('OPERATOR','REVIEWER','AUDITOR')")
    public View current(String ticketId) {
        var row = mapper.findCurrent(ticketId);
        return row == null ? null : new View(row, decode(row.resultJson()), decode(row.findingsJson()),
                decode(mapper.snapshotJson(row.id())));
    }
    private com.fasterxml.jackson.databind.JsonNode decode(String value) {
        try { return value == null ? null : json.readTree(value); }
        catch (Exception invalid) { throw new IllegalStateException("Stored JSON invalid"); }
    }
}
