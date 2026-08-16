package com.devmate.agent.model;

import com.devmate.agent.config.AiReviewProperties;
import com.devmate.agent.service.SpringAiChatClientFactory;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class SpringAiReviewModelTest {

    @Test
    void usesSpringAiStructuredOutputAndPreservesUsageMetadata() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://model.example/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("\"model\":\"qwen-plus\""),
                        org.hamcrest.Matchers.containsString("return JSON"),
                        org.hamcrest.Matchers.containsString("evidence JSON"),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("test-key"))
                )))
                .andRespond(withSuccess("""
                        {
                          "id":"review-1","object":"chat.completion","created":1,"model":"qwen-plus",
                          "choices":[{
                            "index":0,
                            "message":{"role":"assistant","content":"{\\\"findings\\\":[{\\\"chunkId\\\":\\\"42\\\",\\\"category\\\":\\\"CONCURRENCY\\\",\\\"severity\\\":\\\"HIGH\\\",\\\"conclusionType\\\":\\\"INFERENCE\\\",\\\"confidence\\\":0.8,\\\"title\\\":\\\"非原子更新\\\",\\\"evidence\\\":\\\"先查后改\\\",\\\"riskScenario\\\":\\\"并发请求\\\",\\\"suggestion\\\":\\\"原子更新\\\",\\\"verification\\\":\\\"并发测试\\\"}]}"},
                            "finish_reason":"stop"
                          }],
                          "usage":{"prompt_tokens":100,"completion_tokens":50,"total_tokens":150}
                        }
                        """, MediaType.APPLICATION_JSON));

        SpringAiReviewModel model = model(builder);
        AiReviewModelResult result = model.review(new AiReviewPrompt(
                "return JSON", "evidence JSON", "hash"
        ));

        assertThat(result.findings()).hasSize(1);
        assertThat(result.findings().getFirst().chunkId()).isEqualTo("42");
        assertThat(result.totalTokens()).isEqualTo(150);
        assertThat(result.finishReason()).isEqualTo("stop");
        server.verify();
    }

    @Test
    void convertsRateLimitWithoutRetryingPaidRequest() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://model.example/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> model(builder).review(new AiReviewPrompt("JSON", "{}", "hash")))
                .isInstanceOf(AiReviewException.class)
                .hasMessage("AI审查模型调用请求过于频繁或额度不足，请稍后重试并检查额度");
        server.verify();
    }

    private SpringAiReviewModel model(RestClient.Builder builder) {
        AiReviewProperties properties = new AiReviewProperties();
        ModelConnectionSnapshot connection = new ModelConnectionSnapshot(
                "DASHSCOPE", "qwen-plus", "https://model.example/v1", "test-key"
        );
        return new SpringAiReviewModel(
                connection, properties, new SpringAiChatClientFactory(builder, false)
        );
    }
}
