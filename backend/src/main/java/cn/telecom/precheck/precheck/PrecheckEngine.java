package cn.telecom.precheck.precheck;

import cn.telecom.precheck.context.TicketContext;
import cn.telecom.precheck.context.TicketContextProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** 与触发方式无关的预审流水线；调用方负责在模型调用前后划分数据库事务。 */
@Service
@Profile("mysql")
public class PrecheckEngine {
    private final TicketContextProvider provider;
    private final EvidenceVerifier verifier;
    private final PrecheckModel model;
    private final ResultValidator validator;
    private final PromptCatalog prompts;
    private final ObjectMapper json;

    public PrecheckEngine(TicketContextProvider provider, EvidenceVerifier verifier, PrecheckModel model,
            ResultValidator validator, PromptCatalog prompts, ObjectMapper json) {
        this.provider = provider;
        this.verifier = verifier;
        this.model = model;
        this.validator = validator;
        this.prompts = prompts;
        this.json = json;
    }

    public record Outcome(String resultJson, String findingsJson, String actualModel,
                          long durationMs, String failureCode) {}

    public TicketContext load(String ticketId) { return provider.getContext(ticketId); }

    public Outcome analyze(String ticketId, TicketContext context) {
        long duration = 0;
        String resultJson = null;
        String findingsJson = null;
        String actualModel = null;
        String failureCode = null;
        try {
            if (!ticketId.equals(context.ticketId())) throw new PrecheckFailure(PrecheckFailure.Code.INVALID_CONTEXT);
            var report = verifier.verify(context);
            findingsJson = encode(report.findings());
            String payload = encode(Map.of("complaint", context.complaint(), "bills", context.bills(),
                    "subscriptions", context.subscriptions(), "authorizations", context.authorizations(),
                    "ruleFindings", report.findings()));
            long callStarted = System.nanoTime();
            PrecheckModel.Reply reply;
            try { reply = model.call(prompts.systemPrompt(), payload); }
            finally { duration = (System.nanoTime() - callStarted) / 1_000_000; }
            actualModel = reply.model();
            if (actualModel != null && actualModel.length() > 128) {
                actualModel = null;
                throw new PrecheckFailure(PrecheckFailure.Code.INVALID_OUTPUT);
            }
            resultJson = encode(validator.parse(reply.content(), report));
        } catch (PrecheckFailure failure) {
            failureCode = failure.code().name();
        } catch (Exception unexpected) {
            failureCode = PrecheckFailure.Code.MODEL_ERROR.name();
        }
        return new Outcome(resultJson, findingsJson, actualModel, duration, failureCode);
    }

    public String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception invalid) { throw new IllegalStateException("JSON serialization failed"); }
    }
}
