package cn.telecom.precheck.precheck;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

/** 业务字段白名单：不存在退费金额、违规认定或外部业务执行字段。 */
public record PrecheckResult(
        @NotNull ComplaintType complaintType,
        @DecimalMin("0.00") @DecimalMax("99999999.99") @Digits(integer = 8, fraction = 2)
        BigDecimal disputedAmount,
        @NotNull Currency currency,
        @NotBlank @Size(max = 1000) String summary,
        @NotBlank @Size(max = 1000) String evidenceGap,
        @NotBlank @Size(max = 1000) String suggestedAction,
        @NotNull @Size(max = 100) List<@NotBlank @Size(max = 64) String> evidenceIds,
        @NotNull Recommendation recommendation) {
    public enum ComplaintType { VALUE_ADDED_CHARGE_DISPUTE, OTHER, UNCLEAR }
    public enum Currency { CNY }
    public enum Recommendation { VERIFY_AUTHORIZATION, VERIFY_BILLING, MANUAL_REVIEW }
}
