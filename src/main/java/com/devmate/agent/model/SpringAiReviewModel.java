package com.devmate.agent.model;

import com.devmate.agent.config.AiReviewProperties;
import com.devmate.agent.service.SpringAiChatClientFactory;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.List;
import java.util.Locale;
import java.util.Map;

final class SpringAiReviewModel implements AiReviewModel {

    private final ModelConnectionSnapshot connection;
    private final AiReviewProperties properties;
    private final SpringAiChatClientFactory clientFactory;

    SpringAiReviewModel(ModelConnectionSnapshot connection, AiReviewProperties properties,
                        SpringAiChatClientFactory clientFactory) {
        this.connection = connection;
        this.properties = properties;
        this.clientFactory = clientFactory;
    }

    @Override
    public String providerName() {
        return connection.provider();
    }

    @Override
    public String modelName() {
        return connection.model();
    }

    @Override
    public AiReviewModelResult review(AiReviewPrompt prompt) {
        try {
            OpenAiChatOptions.Builder options = OpenAiChatOptions.builder().model(connection.model());
            if ("DASHSCOPE".equals(connection.provider())) {
                options.extraBody(Map.of("enable_thinking", false));
            }
            ResponseEntity<ChatResponse, StructuredFindings> response = clientFactory
                    .createChatClient(
                            connection, options.build(),
                            properties.getConnectTimeout(), properties.getReadTimeout()
                    )
                    .prompt()
                    .system(prompt.systemPrompt())
                    .user(prompt.userPrompt())
                    .call()
                    .responseEntity(StructuredFindings.class);
            if (response.entity() == null) {
                throw new AiReviewException("AI审查模型未返回符合契约的结果");
            }
            ChatResponse chatResponse = response.response();
            Usage usage = chatResponse == null ? null : chatResponse.getMetadata().getUsage();
            String finishReason = chatResponse == null || chatResponse.getResult() == null
                    ? null : chatResponse.getResult().getMetadata().getFinishReason();
            if (finishReason != null) finishReason = finishReason.toLowerCase(Locale.ROOT);
            return new AiReviewModelResult(
                    response.entity().findings(), token(usage, TokenType.PROMPT),
                    token(usage, TokenType.COMPLETION), token(usage, TokenType.TOTAL), finishReason
            );
        } catch (AiReviewException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw SpringAiErrorTranslator.reviewFailure("AI审查模型调用", exception);
        }
    }

    private int token(Usage usage, TokenType type) {
        if (usage == null) return 0;
        Integer value = switch (type) {
            case PROMPT -> usage.getPromptTokens();
            case COMPLETION -> usage.getCompletionTokens();
            case TOTAL -> usage.getTotalTokens();
        };
        return value == null ? 0 : value;
    }

    public record StructuredFindings(List<AiReviewFinding> findings) {
        public StructuredFindings {
            findings = findings == null ? List.of() : List.copyOf(findings);
        }
    }

    private enum TokenType { PROMPT, COMPLETION, TOTAL }
}
