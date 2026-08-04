package com.devmate.agent.model;

import com.devmate.agent.config.AiReviewProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
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

class DashScopeAiReviewModelTest {

    @Test
    void requestsJsonModeAndParsesStructuredFindings() {
        AiReviewProperties properties = properties();
        RestClient.Builder builder = testClientBuilder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://model.example/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "model":"qwen-plus",
                          "messages":[
                            {"role":"system","content":"return JSON"},
                            {"role":"user","content":"evidence JSON"}
                          ],
                          "response_format":{"type":"json_object"},
                          "temperature":0.1,
                          "enable_thinking":false
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "choices":[{
                            "message":{"role":"assistant","content":"{\\\"findings\\\":[{\\\"chunkId\\\":\\\"42\\\",\\\"category\\\":\\\"CONCURRENCY\\\",\\\"severity\\\":\\\"HIGH\\\",\\\"conclusionType\\\":\\\"INFERENCE\\\",\\\"confidence\\\":0.8,\\\"title\\\":\\\"非原子更新\\\",\\\"evidence\\\":\\\"先查后改\\\",\\\"riskScenario\\\":\\\"并发请求\\\",\\\"suggestion\\\":\\\"原子更新\\\",\\\"verification\\\":\\\"并发测试\\\"}]}"},
                            "finish_reason":"stop"
                          }],
                          "usage":{"prompt_tokens":100,"completion_tokens":50,"total_tokens":150}
                        }
                        """, MediaType.APPLICATION_JSON));

        DashScopeAiReviewModel model = new DashScopeAiReviewModel(
                properties,
                new ObjectMapper(),
                builder.build()
        );
        AiReviewModelResult result = model.review(new AiReviewPrompt(
                "return JSON",
                "evidence JSON",
                "hash"
        ));

        assertThat(result.findings()).hasSize(1);
        assertThat(result.findings().getFirst().chunkId()).isEqualTo("42");
        assertThat(result.totalTokens()).isEqualTo(150);
        assertThat(result.finishReason()).isEqualTo("stop");
        server.verify();
    }

    @Test
    void rejectsMissingApiKeyBeforeNetworkCall() {
        AiReviewProperties properties = properties();
        properties.setApiKey("");
        DashScopeAiReviewModel model = new DashScopeAiReviewModel(
                properties,
                new ObjectMapper(),
                RestClient.create()
        );

        assertThatThrownBy(() -> model.review(new AiReviewPrompt("JSON", "{}", "hash")))
                .isInstanceOf(AiReviewException.class)
                .hasMessage("未配置AI审查模型API Key");
    }

    @Test
    void rejectsNonJsonModelOutput() {
        AiReviewProperties properties = properties();
        RestClient.Builder builder = testClientBuilder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://model.example/v1/chat/completions"))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"role":"assistant","content":"not-json"},"finish_reason":"stop"}]}
                        """, MediaType.APPLICATION_JSON));
        DashScopeAiReviewModel model = new DashScopeAiReviewModel(
                properties,
                new ObjectMapper(),
                builder.build()
        );

        assertThatThrownBy(() -> model.review(new AiReviewPrompt("JSON", "{}", "hash")))
                .isInstanceOf(AiReviewException.class)
                .hasMessage("AI审查模型未返回合法JSON");
    }

    private AiReviewProperties properties() {
        AiReviewProperties properties = new AiReviewProperties();
        properties.setBaseUrl("https://model.example/v1");
        properties.setApiKey("test-key");
        properties.setModel("qwen-plus");
        return properties;
    }

    private RestClient.Builder testClientBuilder() {
        return RestClient.builder()
                .baseUrl("https://model.example/v1")
                .defaultHeader("Authorization", "Bearer test-key");
    }
}
