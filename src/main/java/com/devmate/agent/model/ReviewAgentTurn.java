package com.devmate.agent.model;

public record ReviewAgentTurn(
        ReviewAgentMessage message,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        String finishReason
) {
}
