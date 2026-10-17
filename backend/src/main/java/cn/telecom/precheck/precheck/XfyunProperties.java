package cn.telecom.precheck.precheck;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("precheck.xfyun")
@Validated
public record XfyunProperties(String apiKey, @NotBlank String baseUrl,
        @NotBlank String completionsPath, @Size(max=128) String model,
        @Min(100) @Max(120000) int timeoutMs) {
    @Override public String toString() { return "XfyunProperties[credentials redacted]"; }
}
