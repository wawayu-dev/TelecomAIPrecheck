package cn.telecom.precheck.review;
import jakarta.validation.constraints.*;
import cn.telecom.precheck.precheck.PrecheckResult.Recommendation;
public record ReviewRequest(@NotNull @Min(0) Integer expectedVersion, @NotNull Decision decision,
        @NotBlank @Size(max=1000) String summary, @NotNull Recommendation recommendation,
        @NotBlank @Size(max=1000) String reason) {
    public enum Decision { CONFIRMED, REJECTED }
}
