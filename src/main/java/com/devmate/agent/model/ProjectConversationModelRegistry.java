package com.devmate.agent.model;

import com.devmate.agent.config.ProjectConversationProperties;
import com.devmate.agent.service.ModelConnectionService;
import com.devmate.agent.service.SpringAiChatClientFactory;
import org.springframework.stereotype.Component;

@Component
public class ProjectConversationModelRegistry {
    private final ProjectConversationProperties properties;
    private final ModelConnectionService connectionService;
    private final SpringAiChatClientFactory clientFactory;

    public ProjectConversationModelRegistry(ProjectConversationProperties properties,
                                            ModelConnectionService connectionService,
                                            SpringAiChatClientFactory clientFactory) {
        this.properties = properties;
        this.connectionService = connectionService;
        this.clientFactory = clientFactory;
    }

    public ProjectConversationModel current() {
        return create(connectionService.requireActiveConnection());
    }

    public ProjectConversationModel current(String provider, String model) {
        return create(connectionService.requireActiveConnection(provider, model));
    }

    private ProjectConversationModel create(ModelConnectionSnapshot connection) {
        return new SpringAiProjectConversationModel(connection, properties, clientFactory);
    }
}

