package org.ruoyi.service.chat.impl.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ruoyi.common.chat.domain.dto.request.ChatRequest;
import org.ruoyi.common.chat.domain.vo.chat.ChatModelVo;
import org.ruoyi.factory.ChatServiceFactory;
import org.springframework.context.support.StaticApplicationContext;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@Tag("dev")
class VllmServiceImplTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void synchronousAndStreamingRequestsKeepJsonBodyWithoutH2cUpgrade() throws Exception {
        var bodies = new CopyOnWriteArrayList<JsonNode>();
        var upgrades = new CopyOnWriteArrayList<String>();
        var authorizations = new CopyOnWriteArrayList<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            String upgrade = exchange.getRequestHeaders().getFirst("Upgrade");
            upgrades.add(upgrade == null ? "" : upgrade);
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            authorizations.add(authorization == null ? "" : authorization);
            var body = JSON.readTree(exchange.getRequestBody());
            bodies.add(body);
            boolean stream = body.path("stream").asBoolean();
            // Reproduce the Uvicorn failure when the client attempts h2c.
            String answer = upgrade != null
                ? "{\"error\":{\"message\":\"body: Field required\",\"type\":\"Bad Request\"}}"
                : stream
                    ? "data: {\"id\":\"test\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"ok\"}}]}\n\ndata: {\"id\":\"test\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n"
                    : "{\"id\":\"test\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}";
            exchange.getResponseHeaders().set("Content-Type", stream && upgrade == null ? "text/event-stream" : "application/json");
            byte[] bytes = answer.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(upgrade == null ? 200 : 400, stream && upgrade == null ? 0 : bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var provider = new VllmServiceImpl();
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            var config = model(baseUrl + "/v1/chat/completions/");
            assertEquals("ok", provider.buildChatModel(config).chat("local request"));
            config.setApiHost(baseUrl); // Bare origins also normalize to /v1.
            config.setApiKey("test-key");
            var done = new CountDownLatch(1);
            var failure = new AtomicReference<Throwable>();
            var answer = new AtomicReference<String>();
            var request = new ChatRequest();
            request.setEnableThinking(true); // Client/agent preferences must not re-enable reasoning.
            request.setGenerationTemperature(0.1);
            provider.buildStreamingChatModel(config, request).chat("local request", new StreamingChatResponseHandler() {
                @Override public void onPartialResponse(String text) { }
                @Override public void onCompleteResponse(ChatResponse response) {
                    answer.set(response.aiMessage().text());
                    done.countDown();
                }
                @Override public void onError(Throwable error) {
                    failure.set(error);
                    done.countDown();
                }
            });
            assertTrue(done.await(10, TimeUnit.SECONDS), "stream should complete");
            assertNull(failure.get());
            assertEquals("ok", answer.get());
            assertEquals(List.of("", ""), upgrades);
            assertEquals("Bearer test-key", authorizations.get(1));
            assertEquals(2, bodies.size());
            for (var body : bodies) {
                assertEquals("test-vllm-model", body.path("model").asText());
                assertEquals("local request", body.path("messages").get(0).path("content").asText());
                assertTrue(body.path("chat_template_kwargs").has("enable_thinking"));
                assertFalse(body.path("chat_template_kwargs").path("enable_thinking").asBoolean(true));
                assertFalse(body.has("max_tokens"), "the project policy must not impose a 2048-token cap");
            }
            assertTrue(bodies.get(1).path("stream").asBoolean());
            assertEquals(0.1, bodies.get(1).path("temperature").asDouble());
            assertFalse(bodies.get(0).hasNonNull("temperature"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void generationPolicyCannotBeForgedThroughRequestJson() throws Exception {
        var request = JSON.readValue("{\"content\":\"question\",\"generationTemperature\":2.0}", ChatRequest.class);
        assertNull(request.getGenerationTemperature());
        request.setGenerationTemperature(.1);
        request.setEnableThinking(true);
        request.setReasoningEffort("high");
        assertFalse(request.getEnableThinking());
        assertEquals("none", request.getReasoningEffort());
        assertFalse(JSON.readTree(JSON.writeValueAsString(request)).has("generationTemperature"));
    }

    @Test
    void registersAsIndependentProviderAndRejectsMismatchedProtocol() {
        var provider = new VllmServiceImpl();
        try (var context = new StaticApplicationContext()) {
            context.getBeanFactory().registerSingleton("vllmService", provider);
            var factory = new ChatServiceFactory();
            factory.setApplicationContext(context);
            assertSame(provider, factory.getOriginalService("vllm"));
        }
        var config = model("http://localhost:8000/v1");
        config.setProviderCode("custom_api");
        assertThrows(IllegalArgumentException.class, () -> provider.buildChatModel(config));
        config.setProviderCode("vllm");
        config.setApiHost("http://localhost:8000/v1/messages");
        assertThrows(IllegalArgumentException.class, () -> provider.buildChatModel(config));
        config.setApiHost("file:///v1");
        assertThrows(IllegalArgumentException.class, () -> provider.buildStreamingChatModel(config, null));
    }

    private static ChatModelVo model(String baseUrl) {
        var config = new ChatModelVo();
        config.setProviderCode("vllm");
        config.setApiHost(baseUrl);
        config.setModelName("test-vllm-model");
        return config;
    }
}
