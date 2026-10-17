package cn.telecom.precheck.precheck;

import cn.telecom.precheck.context.TicketContextProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import static cn.telecom.precheck.precheck.PrecheckFailure.Code.*;

@Service
@Profile("mysql")
public class PrecheckService {
    private final TicketContextProvider provider;
    private final EvidenceVerifier verifier;
    private final PrecheckModel model;
    private final ResultValidator validator;
    private final PromptCatalog prompts;
    private final PrecheckStore store;
    private final PrecheckMapper mapper;
    private final ObjectMapper json;
    public PrecheckService(TicketContextProvider provider, EvidenceVerifier verifier, PrecheckModel model,
            ResultValidator validator, PromptCatalog prompts, PrecheckStore store, PrecheckMapper mapper, ObjectMapper json) {
        this.provider = provider; this.verifier = verifier; this.model = model; this.validator = validator;
        this.prompts = prompts; this.store = store; this.mapper = mapper; this.json = json;
    }
    public record View(PrecheckRun run, com.fasterxml.jackson.databind.JsonNode result,
                       com.fasterxml.jackson.databind.JsonNode findings, com.fasterxml.jackson.databind.JsonNode evidenceSnapshot) {}
    @PreAuthorize("hasRole('OPERATOR')")
    public View start(String ticketId, String traceId) {
        // 工单整体不存在/宿主失败时不调用模型；已有宿主工单始终保持只读。
        var context = provider.getContext(ticketId);
        String id = UUID.randomUUID().toString();
        store.begin(id, ticketId, model.modelId(), traceId, encode(context));
        long duration = 0;
        String resultJson = null, findingsJson = null, actualModel = null, failureCode = null;
        try {
            if (!ticketId.equals(context.ticketId())) throw new PrecheckFailure(INVALID_CONTEXT);
            var report = verifier.verify(context);
            findingsJson = encode(report.findings());
            // 不发送客户引用或无关宿主身份，仅将本次证据与投诉作为不可信数据传入。
            String payload = encode(Map.of("complaint", context.complaint(), "bills", context.bills(),
                    "subscriptions", context.subscriptions(), "authorizations", context.authorizations(),
                    "ruleFindings", report.findings()));
            long callStarted = System.nanoTime();
            PrecheckModel.Reply reply;
            try { reply = model.call(prompts.systemPrompt(), payload); }
            finally { duration = (System.nanoTime() - callStarted) / 1_000_000; }
            actualModel = reply.model();
            if (actualModel != null && actualModel.length() > 128) {
                actualModel = null; throw new PrecheckFailure(INVALID_OUTPUT);
            }
            resultJson = encode(validator.parse(reply.content(), report));
        } catch (PrecheckFailure failure) { failureCode = failure.code().name(); }
        catch (Exception unexpected) { failureCode = MODEL_ERROR.name(); }
        store.finish(id, failureCode == null ? "SUCCESS" : "FAILED", actualModel, duration,
                failureCode, resultJson, findingsJson);
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
    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception invalid) { throw new IllegalStateException("JSON serialization failed"); }
    }
    private com.fasterxml.jackson.databind.JsonNode decode(String value) {
        try { return value == null ? null : json.readTree(value); }
        catch (Exception invalid) { throw new IllegalStateException("Stored JSON invalid"); }
    }
}
