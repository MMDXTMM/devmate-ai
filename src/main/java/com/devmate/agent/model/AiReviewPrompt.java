package com.devmate.agent.model;

public record AiReviewPrompt(
        String systemPrompt,
        String userPrompt,
        String requestHash
) {
}
