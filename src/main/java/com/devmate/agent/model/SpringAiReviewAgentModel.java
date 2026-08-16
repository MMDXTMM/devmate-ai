package com.devmate.agent.model;

import com.devmate.agent.config.AiReviewProperties;
import com.devmate.agent.service.SpringAiChatClientFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class SpringAiReviewAgentModel implements ReviewAgentModel {

    private final ModelConnectionSnapshot connection;
    private final AiReviewProperties properties;
    private final SpringAiChatClientFactory clientFactory;

    SpringAiReviewAgentModel(ModelConnectionSnapshot connection, AiReviewProperties properties,
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
    public ReviewAgentTurn next(List<ReviewAgentMessage> messages,
                                List<ReviewAgentToolDefinition> tools) {
        try {
            OpenAiChatOptions.Builder options = OpenAiChatOptions.builder()
                    .model(connection.model())
                    .tools(toSpringTools(tools))
                    .toolChoice("auto")
                    .internalToolExecutionEnabled(false);
            if ("DASHSCOPE".equals(connection.provider())) {
                options.extraBody(Map.of("enable_thinking", false));
            }
            ChatResponse response = clientFactory.createChatModel(
                    connection, options.build(),
                    properties.getConnectTimeout(), properties.getReadTimeout()
            ).call(new Prompt(toSpringMessages(messages), options.build()));
            if (response == null || response.getResult() == null) {
                throw new AiReviewException("AI Agent模型未返回可用结果");
            }
            AssistantMessage output = response.getResult().getOutput();
            List<ReviewAgentToolCall> toolCalls = output.getToolCalls().stream()
                    .map(call -> new ReviewAgentToolCall(
                            call.id(), call.type(),
                            new ReviewAgentToolCall.FunctionCall(call.name(), call.arguments())
                    ))
                    .toList();
            if (!StringUtils.hasText(output.getText()) && toolCalls.isEmpty()) {
                throw new AiReviewException("AI Agent模型既未回答也未调用工具");
            }
            Usage usage = response.getMetadata().getUsage();
            return new ReviewAgentTurn(
                    new ReviewAgentMessage("assistant", nullToEmpty(output.getText()), null, toolCalls),
                    token(usage == null ? null : usage.getPromptTokens()),
                    token(usage == null ? null : usage.getCompletionTokens()),
                    token(usage == null ? null : usage.getTotalTokens()),
                    normalizedFinishReason(response.getResult().getMetadata().getFinishReason())
            );
        } catch (AiReviewException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw SpringAiErrorTranslator.reviewFailure("AI Agent模型调用", exception);
        }
    }

    private List<Message> toSpringMessages(List<ReviewAgentMessage> messages) {
        Map<String, String> toolNames = new HashMap<>();
        for (ReviewAgentMessage message : messages) {
            if (message.toolCalls() == null) continue;
            for (ReviewAgentToolCall call : message.toolCalls()) {
                toolNames.put(call.id(), call.function().name());
            }
        }
        List<Message> converted = new ArrayList<>();
        for (ReviewAgentMessage message : messages) {
            switch (message.role()) {
                case "system" -> converted.add(new SystemMessage(nullToEmpty(message.content())));
                case "user" -> converted.add(new UserMessage(nullToEmpty(message.content())));
                case "assistant" -> converted.add(AssistantMessage.builder()
                        .content(nullToEmpty(message.content()))
                        .toolCalls(toSpringToolCalls(message.toolCalls()))
                        .build());
                case "tool" -> converted.add(ToolResponseMessage.builder()
                        .responses(List.of(new ToolResponseMessage.ToolResponse(
                                message.toolCallId(),
                                toolNames.getOrDefault(message.toolCallId(), "unknown_tool"),
                                nullToEmpty(message.content())
                        )))
                        .build());
                default -> throw new AiReviewException("AI Agent消息角色不受支持");
            }
        }
        return List.copyOf(converted);
    }

    private List<AssistantMessage.ToolCall> toSpringToolCalls(List<ReviewAgentToolCall> calls) {
        if (calls == null) return List.of();
        return calls.stream().map(call -> new AssistantMessage.ToolCall(
                call.id(), call.type(), call.function().name(), call.function().arguments()
        )).toList();
    }

    private List<OpenAiApi.FunctionTool> toSpringTools(List<ReviewAgentToolDefinition> tools) {
        return tools.stream().map(tool -> new OpenAiApi.FunctionTool(
                new OpenAiApi.FunctionTool.Function(
                        tool.function().description(), tool.function().name(),
                        tool.function().parameters(), false
                )
        )).toList();
    }

    private int token(Integer value) {
        return value == null ? 0 : value;
    }

    private String normalizedFinishReason(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
