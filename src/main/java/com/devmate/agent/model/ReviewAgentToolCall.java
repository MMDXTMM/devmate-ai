package com.devmate.agent.model;

public record ReviewAgentToolCall(
        String id,
        String type,
        FunctionCall function
) {
    public record FunctionCall(String name, String arguments) {
    }
}
