package com.devmate.agent.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SpringAiChatClientFactoryTest {

    @Test
    void sendsAccountScopedConfigurationThroughSpringAiChatClient() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://model.example/compatible/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer account-secret"))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("\"model\":\"test-model\""),
                        org.hamcrest.Matchers.containsString("系统说明"),
                        org.hamcrest.Matchers.containsString("用户问题"),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("account-secret"))
                )))
                .andRespond(withSuccess("""
                    {
                      "id": "chatcmpl-test",
                      "object": "chat.completion",
                      "created": 1,
                      "model": "test-model",
                      "choices": [{
                        "index": 0,
                        "message": {"role": "assistant", "content": " OK "},
                        "finish_reason": "stop"
                      }],
                      "usage": {"prompt_tokens": 2, "completion_tokens": 1, "total_tokens": 3}
                    }
                    """, MediaType.APPLICATION_JSON));

        SpringAiChatClientFactory factory = new SpringAiChatClientFactory(builder, false);
        String answer = factory.chat(
                "https://model.example/compatible/v1",
                "account-secret", "test-model", "系统说明", "用户问题", null
        );

        assertThat(answer).isEqualTo("OK");
        server.verify();
    }
}
