package com.devmate.agent.model;

import java.util.Map;

public record ReviewAgentToolDefinition(
        String type,
        FunctionDefinition function
) {
    public ReviewAgentToolDefinition(String name, String description, Map<String, Object> parameters) {
        this("function", new FunctionDefinition(name, description, Map.copyOf(parameters)));
    }

    public record FunctionDefinition(
            String name,
            String description,
            Map<String, Object> parameters
    ) {
    }
}
