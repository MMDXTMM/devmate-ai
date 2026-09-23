package com.devmate.agent.model;

import reactor.core.publisher.Flux;

public interface ProjectConversationModel {
    String providerName();
    String modelName();
    Flux<String> stream(ProjectConversationPrompt prompt);
}

