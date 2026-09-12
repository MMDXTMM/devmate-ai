package com.devmate.agent.model;

import java.util.List;

public record ProjectConversationPrompt(
        String systemPrompt,
        List<Message> messages
) {
    public record Message(String role, String content) { }
}

