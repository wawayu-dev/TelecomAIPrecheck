package cn.telecom.precheck.precheck;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class PromptCatalog {
    public static final String VERSION = "charge-dispute-v1";
    private final String prompt;
    public PromptCatalog() throws IOException {
        try (var stream = new ClassPathResource("prompts/" + VERSION + ".txt").getInputStream()) {
            prompt = new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    + "\n输出 JSON Schema：\n" + new BeanOutputConverter<>(PrecheckResult.class).getFormat();
        }
    }
    public String systemPrompt() { return prompt; }
}
