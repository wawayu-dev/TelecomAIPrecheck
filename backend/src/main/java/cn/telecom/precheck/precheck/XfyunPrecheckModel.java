package cn.telecom.precheck.precheck;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.*;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;
import static cn.telecom.precheck.precheck.PrecheckFailure.Code.*;

public final class XfyunPrecheckModel implements PrecheckModel, AutoCloseable {
    private final XfyunProperties properties;
    private final OpenAiChatModel client;
    private final ExecutorService workers = new ThreadPoolExecutor(4, 4, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(16), runnable -> {
                var thread = new Thread(runnable, "xfyun-precheck"); thread.setDaemon(true); return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    public XfyunPrecheckModel(XfyunProperties properties) {
        this.properties = properties;
        if (properties.apiKey() == null || properties.apiKey().isBlank()
                || properties.model() == null || properties.model().isBlank()) {
            client = null;
            return;
        }
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.min(5000, properties.timeoutMs()))).build());
        factory.setReadTimeout(Duration.ofMillis(properties.timeoutMs()));
        var api = OpenAiApi.builder().baseUrl(properties.baseUrl()).completionsPath(properties.completionsPath())
                .apiKey(properties.apiKey()).restClientBuilder(RestClient.builder().requestFactory(factory))
                // 不允许默认错误处理将供应商响应原文带入异常/日志。
                .responseErrorHandler(new org.springframework.web.client.ResponseErrorHandler() {
                    public boolean hasError(org.springframework.http.client.ClientHttpResponse response) throws java.io.IOException {
                        return response.getStatusCode().isError();
                    }
                    public void handleError(java.net.URI uri, org.springframework.http.HttpMethod method,
                            org.springframework.http.client.ClientHttpResponse response) {
                        throw new PrecheckFailure(MODEL_ERROR);
                    }
                }).build();
        client = OpenAiChatModel.builder().openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model(properties.model()).temperature(0.1)
                        .maxTokens(1500).internalToolExecutionEnabled(false).build())
                .retryTemplate(RetryTemplate.builder().maxAttempts(1).build()).build();
    }
    @Override public String modelId() {
        return properties.model() == null || properties.model().isBlank() ? "unconfigured" : properties.model();
    }
    @Override public Reply call(String systemPrompt, String userPayload) {
        if (client == null) throw new PrecheckFailure(MODEL_NOT_CONFIGURED);
        Future<Reply> pending;
        try {
            pending = workers.submit(() -> {
                var response = client.call(new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(userPayload))));
                if (response == null || response.getResult() == null || response.getResult().getOutput() == null)
                    throw new PrecheckFailure(INVALID_OUTPUT);
                if (!"stop".equalsIgnoreCase(response.getResult().getMetadata().getFinishReason()))
                    throw new PrecheckFailure(INVALID_OUTPUT);
                return new Reply(response.getResult().getOutput().getText(), response.getMetadata().getModel());
            });
        } catch (RejectedExecutionException busy) { throw new PrecheckFailure(MODEL_ERROR); }
        try { return pending.get(properties.timeoutMs(), TimeUnit.MILLISECONDS); }
        catch (TimeoutException timeout) { pending.cancel(true); throw new PrecheckFailure(MODEL_TIMEOUT); }
        catch (InterruptedException interrupted) {
            pending.cancel(true); Thread.currentThread().interrupt(); throw new PrecheckFailure(INTERRUPTED);
        } catch (ExecutionException error) {
            Throwable cause = error.getCause();
            for (Throwable current = cause; current != null; current = current.getCause()) {
                if (current instanceof java.net.http.HttpTimeoutException || current instanceof java.net.SocketTimeoutException)
                    throw new PrecheckFailure(MODEL_TIMEOUT);
                if (current instanceof PrecheckFailure failure) throw failure;
            }
            throw new PrecheckFailure(MODEL_ERROR);
        }
    }
    @Override public void close() { workers.shutdownNow(); }
}
