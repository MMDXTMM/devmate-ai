package com.devmate.agent.model;

import com.devmate.agent.config.ProjectConversationProperties;
import com.devmate.agent.service.SpringAiChatClientFactory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringAiProjectConversationModelTest {

    @Test
    void streamsTokensAndMapsMultiTurnHistoryIntoSpringAiMessages() {
        SpringAiChatClientFactory factory = mock(SpringAiChatClientFactory.class);
        ChatClient client = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.StreamResponseSpec stream = mock(ChatClient.StreamResponseSpec.class);
        when(factory.createChatClient(any(), any(), any(), any())).thenReturn(client);
        when(client.prompt()).thenReturn(request);
        when(request.messages(any(List.class))).thenReturn(request);
        when(request.stream()).thenReturn(stream);
        when(stream.content()).thenReturn(Flux.just("订单", "流程"));

        SpringAiProjectConversationModel model = new SpringAiProjectConversationModel(
                new ModelConnectionSnapshot("DASHSCOPE", "qwen-plus", "https://model.example/v1", "test-key"),
                new ProjectConversationProperties(), factory
        );
        List<String> tokens = model.stream(new ProjectConversationPrompt("只基于代码证据回答", List.of(
                new ProjectConversationPrompt.Message("USER", "上一轮问题"),
                new ProjectConversationPrompt.Message("ASSISTANT", "上一轮回答"),
                new ProjectConversationPrompt.Message("USER", "当前追问")
        ))).collectList().block();

        assertThat(tokens).containsExactly("订单", "流程");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Message>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(request).messages(messagesCaptor.capture());
        assertThat(messagesCaptor.getValue())
                .extracting(Message::getMessageType, Message::getText)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(MessageType.SYSTEM, "只基于代码证据回答"),
                        org.assertj.core.groups.Tuple.tuple(MessageType.USER, "上一轮问题"),
                        org.assertj.core.groups.Tuple.tuple(MessageType.ASSISTANT, "上一轮回答"),
                        org.assertj.core.groups.Tuple.tuple(MessageType.USER, "当前追问")
                );
    }
}
