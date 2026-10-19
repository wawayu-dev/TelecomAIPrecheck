package cn.telecom.precheck.review;

import cn.telecom.precheck.precheck.PrecheckResult;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ReviewRequestTest {
    @Test void contradictoryConclusionAndActionAreRejected() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var request = new ReviewRequest(1, ReviewRequest.Decision.CONFIRMED,
                    ReviewRequest.EvidenceConclusion.INSUFFICIENT, ReviewRequest.NextAction.SUBMIT_CONCLUSION,
                    "摘要", PrecheckResult.Recommendation.VERIFY_AUTHORIZATION, "依据");
            assertThat(factory.getValidator().validate(request)).extracting(v -> v.getMessage())
                    .contains("证据结论与下一步处理不一致");
        }
    }

    @Test void escalationAcceptsConflictEvidence() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var request = new ReviewRequest(1, ReviewRequest.Decision.ESCALATED,
                    ReviewRequest.EvidenceConclusion.CONFLICT, ReviewRequest.NextAction.ESCALATE,
                    "摘要", PrecheckResult.Recommendation.MANUAL_REVIEW, "依据");
            assertThat(factory.getValidator().validate(request)).isEmpty();
        }
    }
}
