package com.devmate.agent.model;

import com.devmate.agent.config.AiReviewProperties;
import com.devmate.agent.service.SpringAiChatClientFactory;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SpringAiReviewAgentModelTest {

    @Test
    void sendsSpringAiToolDefinitionsAndReturnsToolCallForJavaOrchestrator() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://model.example/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("\"name\":\"searchCode\""),
                        org.hamcrest.Matchers.containsString("\"tool_choice\":\"auto\""),
                        org.hamcrest.Matchers.containsString("调查库存风险")
                )))
                .andRespond(withSuccess("""
                        {
                          "id":"agent-1","object":"chat.completion","created":1,"model":"qwen-plus",
                          "choices":[{
                            "index":0,
                            "message":{"role":"assistant","content":"","tool_calls":[{
                              "id":"call-1","type":"function",
                              "function":{"name":"searchCode","arguments":"{\\\"query\\\":\\\"库存并发扣减\\\"}"}
                            }]},
                            "finish_reason":"tool_calls"
                          }],
                          "usage":{"prompt_tokens":20,"completion_tokens":8,"total_tokens":28}
                        }
                        """, MediaType.APPLICATION_JSON));

        ReviewAgentTurn turn = model(builder).next(
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
    void mapsAssistantAndToolHistoryBackIntoSpringAiMessages() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://model.example/v1/chat/completions"))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("call-previous"),
                        org.hamcrest.Matchers.containsString("searchCode"),
                        org.hamcrest.Matchers.containsString("检索结果")
                )))
                .andRespond(withSuccess("""
                        {
                          "id":"agent-2","object":"chat.completion","created":1,"model":"qwen-plus",
                          "choices":[{"index":0,"message":{"role":"assistant","content":"取证完成"},"finish_reason":"stop"}]
                        }
                        """, MediaType.APPLICATION_JSON));

        ReviewAgentToolCall previousCall = new ReviewAgentToolCall(
                "call-previous", "function",
                new ReviewAgentToolCall.FunctionCall("searchCode", "{\"query\":\"库存\"}")
        );
        ReviewAgentTurn turn = model(builder).next(List.of(
                ReviewAgentMessage.user("调查"),
                new ReviewAgentMessage("assistant", "", null, List.of(previousCall)),
                ReviewAgentMessage.tool("call-previous", "检索结果")
        ), List.of());

        assertThat(turn.message().content()).isEqualTo("取证完成");
        assertThat(turn.message().toolCalls()).isNull();
        server.verify();
    }

    private SpringAiReviewAgentModel model(RestClient.Builder builder) {
        ModelConnectionSnapshot connection = new ModelConnectionSnapshot(
                "DASHSCOPE", "qwen-plus", "https://model.example/v1", "test-key"
        );
        return new SpringAiReviewAgentModel(
                connection, new AiReviewProperties(), new SpringAiChatClientFactory(builder, false)
        );
    }
}
