package com.devmate.agent.dto;

public record ModelConnectionTestResponse(
        String provider,
        String model,
        long latencyMs,
        String message
) {
}
