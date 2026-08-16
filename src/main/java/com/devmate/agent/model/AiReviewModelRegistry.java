package com.devmate.agent.model;

import com.devmate.agent.config.AiReviewProperties;
import com.devmate.agent.service.ModelConnectionService;
import com.devmate.agent.service.SpringAiChatClientFactory;
import org.springframework.stereotype.Component;

@Component
public class AiReviewModelRegistry {

    private final AiReviewProperties properties;
    private final ModelConnectionService connectionService;
    private final SpringAiChatClientFactory clientFactory;

    public AiReviewModelRegistry(AiReviewProperties properties,
                                 ModelConnectionService connectionService,
                                 SpringAiChatClientFactory clientFactory) {
        this.properties = properties;
        this.connectionService = connectionService;
        this.clientFactory = clientFactory;
    }

    public AiReviewModel current() {
        return new SpringAiReviewModel(connectionService.requireActiveConnection(), properties, clientFactory);
    }
}
