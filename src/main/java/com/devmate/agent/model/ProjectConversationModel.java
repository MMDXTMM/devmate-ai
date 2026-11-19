package com.devmate.agent.model;

import reactor.core.publisher.Flux;

public interface ProjectConversationModel {
    String providerName();
    String modelName();
    Flux<String> stream(ProjectConversationPrompt prompt);
    default boolean supportsResearch() { return false; }
    default reactor.core.publisher.Mono<ReviewAgentTurn> research(
            java.util.List<ReviewAgentMessage> messages, java.util.List<ReviewAgentToolDefinition> tools) {
        return reactor.core.publisher.Mono.empty();
    }
}

