package com.devmate.agent.model;

import com.devmate.agent.config.AiReviewProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class DashScopeReviewAgentModelTest {

    @Test
    void sendsToolDefinitionsAndParsesToolCall() {
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
                          "messages":[{"role":"user","content":"调查库存风险"}],
                          "tools":[{"type":"function","function":{"name":"searchCode","description":"检索代码","parameters":{"type":"object"}}}],
                          "tool_choice":"auto",
                          "temperature":0.1,
                          "enable_thinking":false
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "choices":[{
                            "message":{"role":"assistant","content":"","tool_calls":[{
                              "id":"call-1","type":"function",
                              "function":{"name":"searchCode","arguments":"{\\\"query\\\":\\\"库存并发扣减\\\"}"}
                            }]},
                            "finish_reason":"tool_calls"
                          }],
                          "usage":{"prompt_tokens":20,"completion_tokens":8,"total_tokens":28}
                        }
                        """, MediaType.APPLICATION_JSON));

        DashScopeReviewAgentModel model = new DashScopeReviewAgentModel(properties, builder.build());
        ReviewAgentTurn turn = model.next(
                List.of(ReviewAgentMessage.user("调查库存风险")),
                List.of(new ReviewAgentToolDefinition(
                        "searchCode", "检索代码", Map.of("type", "object")
                ))
        );

        assertThat(turn.message().toolCalls()).hasSize(1);
        assertThat(turn.message().toolCalls().getFirst().id()).isEqualTo("call-1");
        assertThat(turn.message().toolCalls().getFirst().function().name()).isEqualTo("searchCode");
        assertThat(turn.totalTokens()).isEqualTo(28);
        assertThat(turn.finishReason()).isEqualTo("tool_calls");
        server.verify();
    }

    @Test
    void parsesFinalAssistantMessage() {
        AiReviewProperties properties = properties();
        RestClient.Builder builder = testClientBuilder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://model.example/v1/chat/completions"))
                .andRespond(withSuccess("""
                        {
                          "choices":[{
                            "message":{"role":"assistant","content":"取证完成"},
                            "finish_reason":"stop"
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        ReviewAgentTurn turn = new DashScopeReviewAgentModel(properties, builder.build())
                .next(List.of(ReviewAgentMessage.user("继续")), List.of());

        assertThat(turn.message().content()).isEqualTo("取证完成");
        assertThat(turn.message().toolCalls()).isNull();
        assertThat(turn.totalTokens()).isZero();
        server.verify();
    }

    @Test
    void rejectsMissingApiKeyBeforeNetworkCall() {
        AiReviewProperties properties = properties();
        properties.setApiKey("");
        DashScopeReviewAgentModel model = new DashScopeReviewAgentModel(
                properties, RestClient.create()
        );

        assertThatThrownBy(() -> model.next(List.of(), List.of()))
                .isInstanceOf(AiReviewException.class)
                .hasMessage("未配置AI审查模型API Key");
    }

    @Test
    void explainsRateLimitWithoutRetryingAgentTurns() {
        AiReviewProperties properties = properties();
        RestClient.Builder builder = testClientBuilder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://model.example/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        DashScopeReviewAgentModel model = new DashScopeReviewAgentModel(properties, builder.build());

        assertThatThrownBy(() -> model.next(List.of(), List.of()))
                .isInstanceOf(AiReviewException.class)
                .hasMessage("AI Agent模型请求过于频繁或额度不足，请稍后重试并检查额度");
        server.verify();
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
