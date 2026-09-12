package com.devmate.agent.model;

import com.devmate.agent.config.ProjectConversationProperties;
import com.devmate.agent.service.SpringAiChatClientFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.openai.OpenAiChatOptions;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class SpringAiProjectConversationModel implements ProjectConversationModel {
    private final ModelConnectionSnapshot connection;
    private final ProjectConversationProperties properties;
    private final SpringAiChatClientFactory clientFactory;

    SpringAiProjectConversationModel(ModelConnectionSnapshot connection,
                                     ProjectConversationProperties properties,
                                     SpringAiChatClientFactory clientFactory) {
        this.connection = connection;
        this.properties = properties;
        this.clientFactory = clientFactory;
    }

    @Override
    public String providerName() { return connection.provider(); }

    @Override
    public String modelName() { return connection.model(); }

    @Override
    public Flux<String> stream(ProjectConversationPrompt prompt) {
        try {
            OpenAiChatOptions.Builder options = OpenAiChatOptions.builder().model(connection.model());
            if ("DASHSCOPE".equals(connection.provider())) {
                options.extraBody(Map.of("enable_thinking", false));
            }
            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(prompt.systemPrompt()));
            for (ProjectConversationPrompt.Message item : prompt.messages()) {
                if ("ASSISTANT".equals(item.role())) {
                    messages.add(new AssistantMessage(item.content()));
                } else {
                    messages.add(new UserMessage(item.content()));
                }
            }
            return clientFactory.createChatClient(
                            connection, options.build(), properties.getConnectTimeout(), properties.getReadTimeout()
                    ).prompt()
                    .messages(messages)
                    .stream()
                    .content()
                    .onErrorMap(RuntimeException.class,
                            exception -> SpringAiErrorTranslator.reviewFailure("项目对话模型调用", exception));
        } catch (RuntimeException exception) {
            return Flux.error(SpringAiErrorTranslator.reviewFailure("项目对话模型调用", exception));
        }
    }
}

