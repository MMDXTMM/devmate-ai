package com.devmate.agent.model;

import com.devmate.agent.config.AiReviewProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

@Component
public class DashScopeAiReviewModel implements AiReviewModel {

    private final AiReviewProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    @Autowired
    public DashScopeAiReviewModel(
            AiReviewProperties properties,
            ObjectMapper objectMapper,
            RestClient.Builder restClientBuilder
    ) {
        this(properties, objectMapper, buildClient(properties, restClientBuilder));
    }

    DashScopeAiReviewModel(
            AiReviewProperties properties,
            ObjectMapper objectMapper,
            RestClient restClient
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
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
    public AiReviewModelResult review(AiReviewPrompt prompt) {
        validateConfiguration();
        try {
            ChatCompletionResponse response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ChatCompletionRequest(
                            properties.getModel(),
                            List.of(
                                    new ChatMessage("system", prompt.systemPrompt()),
                                    new ChatMessage("user", prompt.userPrompt())
                            ),
                            Map.of("type", "json_object"),
                            0.1,
                            false
                    ))
                    .retrieve()
                    .body(ChatCompletionResponse.class);
            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                throw new AiReviewException("AI审查模型未返回可用结果");
            }
            Choice choice = response.choices().getFirst();
            if (choice.message() == null || !StringUtils.hasText(choice.message().content())) {
                throw new AiReviewException("AI审查模型返回内容为空");
            }
            StructuredFindings structured = objectMapper.readValue(
                    choice.message().content(),
                    StructuredFindings.class
            );
            Usage usage = response.usage() == null ? new Usage(0, 0, 0) : response.usage();
            return new AiReviewModelResult(
                    structured.findings(),
                    usage.promptTokens(),
                    usage.completionTokens(),
                    usage.totalTokens(),
                    choice.finishReason()
            );
        } catch (JsonProcessingException exception) {
            throw new AiReviewException("AI审查模型未返回合法JSON", exception);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 429) {
                throw new AiReviewException("AI审查模型请求过于频繁或额度不足，请稍后重试并检查额度", exception);
            }
            throw new AiReviewException("AI审查模型调用失败", exception);
        } catch (RestClientException exception) {
            throw new AiReviewException("AI审查模型调用失败", exception);
        }
    }

    private void validateConfiguration() {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new AiReviewException("未配置AI审查模型API Key");
        }
        if (!StringUtils.hasText(properties.getBaseUrl())
                || !properties.getBaseUrl().startsWith("https://")) {
            throw new AiReviewException("AI审查模型地址必须是HTTPS");
        }
        if (!StringUtils.hasText(properties.getModel())) {
            throw new AiReviewException("未配置AI审查模型名称");
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
            List<ChatMessage> messages,
            @JsonProperty("response_format") Map<String, String> responseFormat,
            double temperature,
            @JsonProperty("enable_thinking") boolean enableThinking
    ) {
    }

    record ChatMessage(String role, String content) {
    }

    record ChatCompletionResponse(List<Choice> choices, Usage usage) {
    }

    record Choice(
            ChatMessage message,
            @JsonProperty("finish_reason") String finishReason
    ) {
    }

    record Usage(
            @JsonProperty("prompt_tokens") int promptTokens,
            @JsonProperty("completion_tokens") int completionTokens,
            @JsonProperty("total_tokens") int totalTokens
    ) {
    }

    record StructuredFindings(List<AiReviewFinding> findings) {
        StructuredFindings {
            findings = findings == null ? List.of() : List.copyOf(findings);
        }
    }
}
