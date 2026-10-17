package cn.telecom.precheck.precheck;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.core.JsonParser;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import static cn.telecom.precheck.precheck.PrecheckFailure.Code.*;

@Component
public class ResultValidator {
    private final ObjectMapper mapper;
    private final Validator validator;
    public ResultValidator(ObjectMapper mapper, Validator validator) {
        this.mapper = mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
        this.validator = validator;
    }
    public PrecheckResult parse(String json, EvidenceVerifier.Report report) {
        PrecheckResult result;
        try {
            if (json == null || json.length() > 16000) throw new PrecheckFailure(INVALID_OUTPUT);
            result = mapper.readValue(json, PrecheckResult.class);
            if (result == null || !validator.validate(result).isEmpty()) throw new PrecheckFailure(INVALID_OUTPUT);
        } catch (Exception invalid) { throw new PrecheckFailure(INVALID_OUTPUT); }
        if ((!report.evidenceIds().isEmpty() && result.evidenceIds().isEmpty())
                || !report.evidenceIds().containsAll(result.evidenceIds())
                || result.evidenceIds().stream().distinct().count() != result.evidenceIds().size()) {
            throw new PrecheckFailure(INVALID_EVIDENCE_REFERENCE);
        }
        // 授权不完整时使用代码生成的中性摘要和建议，避免模型输出直接认定违规。
        if (report.authorizationIncomplete()) {
            return new PrecheckResult(result.complaintType(), result.disputedAmount(), result.currency(),
                    "客户提出扣费争议；本次关联授权证据不完整，需人工进一步核验，不能据此认定违规扣费。",
                    result.evidenceIds(), PrecheckResult.Recommendation.VERIFY_AUTHORIZATION);
        }
        return result;
    }
}
