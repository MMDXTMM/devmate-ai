package com.devmate.agent.model;

import com.devmate.agent.config.AiReviewProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

@Component
public class DashScopeReviewAgentModel implements ReviewAgentModel {

    private final AiReviewProperties properties;
    private final RestClient restClient;

    @Autowired
    public DashScopeReviewAgentModel(
            AiReviewProperties properties,
            RestClient.Builder restClientBuilder
    ) {
        this(properties, buildClient(properties, restClientBuilder));
    }

    DashScopeReviewAgentModel(AiReviewProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    @Override
    public String providerName() {
        return "DASHSCOPE";
    }

    @Override
    public String modelName() {
        return properties.getModel();
    }

    @Override
    public ReviewAgentTurn next(
            List<ReviewAgentMessage> messages,
            List<ReviewAgentToolDefinition> tools
    ) {
        validateConfiguration();
        try {
            ChatCompletionResponse response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ChatCompletionRequest(
                            properties.getModel(),
                            List.copyOf(messages),
                            List.copyOf(tools),
                            "auto",
                            0.1,
                            false
                    ))
                    .retrieve()
                    .body(ChatCompletionResponse.class);
            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                throw new AiReviewException("AI Agent模型未返回可用结果");
            }
            Choice choice = response.choices().getFirst();
            if (choice.message() == null) {
                throw new AiReviewException("AI Agent模型返回消息为空");
            }
            ReviewAgentMessage message = new ReviewAgentMessage(
                    "assistant",
                    nullToEmpty(choice.message().content()),
                    null,
                    choice.message().toolCalls()
            );
            if (!StringUtils.hasText(message.content())
                    && (message.toolCalls() == null || message.toolCalls().isEmpty())) {
                throw new AiReviewException("AI Agent模型既未回答也未调用工具");
            }
            Usage usage = response.usage() == null ? new Usage(0, 0, 0) : response.usage();
            return new ReviewAgentTurn(
                    message,
                    usage.promptTokens(),
                    usage.completionTokens(),
                    usage.totalTokens(),
                    choice.finishReason()
            );
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 429) {
                throw new AiReviewException("AI Agent模型请求过于频繁或额度不足，请稍后重试并检查额度", exception);
            }
            throw new AiReviewException("AI Agent模型调用失败", exception);
        } catch (RestClientException exception) {
            throw new AiReviewException("AI Agent模型调用失败", exception);
        }
    }

    private void validateConfiguration() {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new AiReviewException("未配置AI审查模型API Key");
        }
        if (!StringUtils.hasText(properties.getBaseUrl())
                || !properties.getBaseUrl().startsWith("https://")) {
            throw new AiReviewException("AI Agent模型地址必须是HTTPS");
        }
        if (!StringUtils.hasText(properties.getModel())) {
            throw new AiReviewException("未配置AI Agent模型名称");
        }
    }

    private static RestClient buildClient(
            AiReviewProperties properties,
            RestClient.Builder restClientBuilder
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());
        return restClientBuilder.clone()
                .requestFactory(requestFactory)
                .baseUrl(trimTrailingSlash(properties.getBaseUrl()))
                .defaultHeader("Authorization", "Bearer " + nullToEmpty(properties.getApiKey()))
                .build();
    }

    private static String trimTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return "https://invalid.local";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    record ChatCompletionRequest(
            String model,
            List<ReviewAgentMessage> messages,
            List<ReviewAgentToolDefinition> tools,
            @JsonProperty("tool_choice") String toolChoice,
            double temperature,
            @JsonProperty("enable_thinking") boolean enableThinking
    ) {
    }

    record ChatCompletionResponse(List<Choice> choices, Usage usage) {
    }

    record Choice(
            ReviewAgentMessage message,
            @JsonProperty("finish_reason") String finishReason
    ) {
    }

    record Usage(
            @JsonProperty("prompt_tokens") int promptTokens,
            @JsonProperty("completion_tokens") int completionTokens,
            @JsonProperty("total_tokens") int totalTokens
    ) {
    }
}
