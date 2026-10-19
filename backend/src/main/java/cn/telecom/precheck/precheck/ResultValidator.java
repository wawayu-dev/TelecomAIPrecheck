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
            result = mapper.readValue(unwrapJsonFence(json), PrecheckResult.class);
            if (result == null || !validator.validate(result).isEmpty()) throw new PrecheckFailure(INVALID_OUTPUT);
        } catch (Exception invalid) { throw new PrecheckFailure(INVALID_OUTPUT); }
        if ((!report.evidenceIds().isEmpty() && result.evidenceIds().isEmpty())
                || !report.evidenceIds().containsAll(result.evidenceIds())
                || result.evidenceIds().stream().distinct().count() != result.evidenceIds().size()) {
            throw new PrecheckFailure(INVALID_EVIDENCE_REFERENCE);
        }
        // 授权不完整时使用代码生成的中性证据缺口和动作，避免模型输出直接认定违规。
        if (report.authorizationIncomplete()) {
            return new PrecheckResult(result.complaintType(), result.disputedAmount(), result.currency(),
                    result.summary(),
                    "本次关联授权证据不完整，现有材料无法证明客户主动订购，不能据此认定违规扣费。",
                    "补充授权凭证，并核实授权时间、验证方式、操作主体及其与订购流水的关联关系。",
                    result.evidenceIds(), PrecheckResult.Recommendation.VERIFY_AUTHORIZATION);
        }
        return result;
    }

    private String unwrapJsonFence(String value) {
        String trimmed = value.trim();
        if (!trimmed.startsWith("```")) return trimmed;
        int firstLineEnd = trimmed.indexOf('\n');
        if (firstLineEnd < 0 || !trimmed.endsWith("```")) throw new PrecheckFailure(INVALID_OUTPUT);
        String opening = trimmed.substring(3, firstLineEnd).trim();
        if (!opening.isEmpty() && !opening.equalsIgnoreCase("json")) throw new PrecheckFailure(INVALID_OUTPUT);
        String body = trimmed.substring(firstLineEnd + 1, trimmed.length() - 3).trim();
        if (body.contains("```")) throw new PrecheckFailure(INVALID_OUTPUT);
        return body;
    }
}
