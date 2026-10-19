package cn.telecom.precheck.review;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import cn.telecom.precheck.precheck.PrecheckResult.Recommendation;
public record ReviewRequest(@NotNull @Min(0) Integer expectedVersion, @NotNull Decision decision,
        @NotNull EvidenceConclusion evidenceConclusion, @NotNull NextAction nextAction,
        @NotBlank @Size(max=1000) String summary, @NotNull Recommendation recommendation,
        @NotBlank @Size(max=1000) String reason) {
    public enum Decision { CONFIRMED, REJECTED, ESCALATED }
    public enum EvidenceConclusion { SUFFICIENT, INSUFFICIENT, CONFLICT }
    public enum NextAction { REQUEST_EVIDENCE, SUBMIT_CONCLUSION, ESCALATE }

    public ReviewRequest(Integer expectedVersion, Decision decision, String summary,
            Recommendation recommendation, String reason) {
        this(expectedVersion, decision,
                decision == Decision.CONFIRMED ? EvidenceConclusion.SUFFICIENT : EvidenceConclusion.INSUFFICIENT,
                decision == Decision.CONFIRMED ? NextAction.SUBMIT_CONCLUSION : NextAction.REQUEST_EVIDENCE,
                summary, recommendation, reason);
    }

    @AssertTrue(message="证据结论与下一步处理不一致")
    @JsonIgnore
    public boolean isDecisionConsistent() {
        if (decision == null || evidenceConclusion == null || nextAction == null) return true;
        return decision == Decision.CONFIRMED && evidenceConclusion == EvidenceConclusion.SUFFICIENT
                    && nextAction == NextAction.SUBMIT_CONCLUSION
                || decision == Decision.REJECTED && evidenceConclusion != EvidenceConclusion.SUFFICIENT
                    && nextAction == NextAction.REQUEST_EVIDENCE
                || decision == Decision.ESCALATED && nextAction == NextAction.ESCALATE;
    }
}
