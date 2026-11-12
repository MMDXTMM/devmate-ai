package com.devmate.agent.service;

import com.devmate.agent.model.ModelConnectionSnapshot;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompatibleStreamingTest {

    @Test
    void acceptsTerminalMessageInsteadOfDeltaWithoutRepeatingTheAnswer() throws Exception {
        assertThat(stream("COMPATIBLE", """
                data: {"id":"test","choices":[{"index":0,"delta":{"role":"assistant","content":"订单流程"}}]}

                data: {"id":"test","choices":[{"index":0,"message":{"role":"assistant","content":"订单流程"},"finish_reason":"stop"}],"usage":{"prompt_tokens":2,"completion_tokens":2,"total_tokens":4}}

                data: [DONE]

                """)).isEqualTo("订单流程");
    }

    @Test
    void preservesStandardDeltasAndUsageOnlyEvents() throws Exception {
        assertThat(stream("OPENAI", """
                data: {"id":"test","choices":[{"index":0,"delta":{"role":"assistant","content":"正常回答"}}]}

                data: {"id":"test","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

                data: {"id":"test","choices":[],"usage":{"prompt_tokens":2,"completion_tokens":2,"total_tokens":4}}

                data: [DONE]

                """)).isEqualTo("正常回答");
    }

    @Test
    void rejectsMissingDeltaInAnUnfinishedChoice() {
        assertThatThrownBy(() -> stream("COMPATIBLE", """
                data: {"id":"test","choices":[{"index":0,"message":{"content":"invalid"}}]}

                data: [DONE]

                """ )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("stream");
    }

    private String stream(String provider, String events) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = events.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var body = exchange.getResponseBody()) { body.write(bytes); }
        });
        server.start();
        try {
            var factory = new SpringAiChatClientFactory(RestClient.builder());
            var responses = factory.createChatClient(new ModelConnectionSnapshot(provider, "test-model",
                            "http://127.0.0.1:" + server.getAddress().getPort() + "/v1", "test-key"),
                    OpenAiChatOptions.builder().model("test-model").build(),
                    Duration.ofSeconds(2), Duration.ofSeconds(5))
                    .prompt().user("问题").stream().chatResponse().collectList()
                    .block(Duration.ofSeconds(10));
            assertThat(responses.stream().flatMap(response -> response.getResults().stream())
                    .map(generation -> generation.getMetadata().getFinishReason()))
                    .contains("STOP");
            return responses.stream().flatMap(response -> response.getResults().stream())
                    .map(generation -> generation.getOutput().getText())
                    .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.joining());
        } finally {
            server.stop(0);
        }
    }
}
