package cn.telecom.precheck.precheck;

import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class XfyunPrecheckModelTest {
    HttpServer server;
    final ObjectMapper json=new ObjectMapper();
    final AtomicReference<String> body=new AtomicReference<>();
    final AtomicReference<String> authorization=new AtomicReference<>();
    final AtomicInteger calls=new AtomicInteger();
    @AfterEach void stop(){ if(server!=null)server.stop(0); }
    void server(int status,String content,int delay) throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v2/chat/completions", exchange -> {
            calls.incrementAndGet();
            body.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            try { Thread.sleep(delay); } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
            byte[] bytes=content.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            try { exchange.sendResponseHeaders(status,bytes.length); exchange.getResponseBody().write(bytes); }
            finally { exchange.close(); }
        });
        server.start();
    }
    XfyunPrecheckModel client(int timeout) {
        return new XfyunPrecheckModel(new XfyunProperties("synthetic-test-key",
                "http://127.0.0.1:"+server.getAddress().getPort(),"/v2/chat/completions","test-model",timeout));
    }
    String response(String text) throws Exception {
        return json.writeValueAsString(Map.of("id","test","object","chat.completion","created",1,"model","reported-model",
                "choices",List.of(Map.of("index",0,"message",Map.of("role","assistant","content",text),"finish_reason","stop"))));
    }
    @Test void actualSpringAiClientUsesConfiguredPathModelAndSystemRole() throws Exception {
        server(200,response(Samples.VALID),0);
        try(var model=client(5000)) {
            var reply=model.call("system rules","synthetic data");
            assertThat(reply.content()).isEqualTo(Samples.VALID);
            assertThat(reply.model()).isEqualTo("reported-model");
            var request=json.readTree(body.get());
            assertThat(request.path("model").asText()).isEqualTo("test-model");
            assertThat(request.path("messages").get(0).path("role").asText()).isEqualTo("system");
            assertThat(request.has("tools")).isFalse();
            assertThat(authorization.get()).isEqualTo("Bearer synthetic-test-key");
        }
    }
    @Test void upstreamErrorsAreNotRetriedOrLeaked() throws Exception {
        server(401,"{\"error\":\"secret vendor error\"}",0);
        try(var model=client(5000)) {
            assertThatThrownBy(() -> model.call("s","u")).hasMessage("MODEL_ERROR");
            assertThat(calls.get()).isEqualTo(1);
        }
    }
    @Test void timeoutIsBounded() throws Exception {
        server(200,response(Samples.VALID),1500);
        try(var model=client(150)) {
            long start=System.nanoTime();
            assertThatThrownBy(() -> model.call("s","u")).hasMessage("MODEL_TIMEOUT");
            assertThat((System.nanoTime()-start)/1_000_000).isLessThan(1200);
        }
    }
    @Test void malformedProtocolResponseFailsSafely() throws Exception {
        server(200,"{not-json}",0);
        try(var model=client(5000)) {
            assertThatThrownBy(() -> model.call("s","u")).hasMessage("MODEL_ERROR");
        }
    }
    @Test void truncatedModelCompletionIsRejectedEvenWhenJsonLooksValid() throws Exception {
        server(200,response(Samples.VALID).replace("\"finish_reason\":\"stop\"","\"finish_reason\":\"length\""),0);
        try(var model=client(5000)) {
            assertThatThrownBy(() -> model.call("s","u")).hasMessage("INVALID_OUTPUT");
        }
    }
    @Test void missingCredentialsNeverPretendToBeSuccessful() {
        try(var model=new XfyunPrecheckModel(new XfyunProperties("","https://example.invalid","/v2/chat/completions","",1000))) {
            assertThatThrownBy(() -> model.call("s","u")).hasMessage("MODEL_NOT_CONFIGURED");
        }
    }
}
