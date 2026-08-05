package com.devmate.agent.model;

public record AiInvocationMetrics(
        int totalTokens,
        long latencyMs
) {
}
