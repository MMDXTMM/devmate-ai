package com.devmate.agent.model;

import com.devmate.agent.config.ProjectUnderstandingProperties;
import com.devmate.agent.service.ModelConnectionService;
import com.devmate.agent.service.SpringAiChatClientFactory;
import org.springframework.stereotype.Component;

@Component
public class ProjectUnderstandingModelRegistry {
    private final ProjectUnderstandingProperties properties;
    private final ModelConnectionService connectionService;
    private final SpringAiChatClientFactory clientFactory;

    public ProjectUnderstandingModelRegistry(
            ProjectUnderstandingProperties properties,
            ModelConnectionService connectionService,
            SpringAiChatClientFactory clientFactory
    ) {
        this.properties = properties;
        this.connectionService = connectionService;
        this.clientFactory = clientFactory;
    }

    public ProjectUnderstandingModel current() {
        return new SpringAiProjectUnderstandingModel(
                connectionService.requireActiveConnection(), properties, clientFactory
        );
    }
}
