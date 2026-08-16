package com.devmate.agent.model;

import com.devmate.agent.config.AiReviewProperties;
import com.devmate.agent.service.ModelConnectionService;
import com.devmate.agent.service.SpringAiChatClientFactory;
import org.springframework.stereotype.Component;

@Component
public class ReviewAgentModelRegistry {

    private final AiReviewProperties properties;
    private final ModelConnectionService connectionService;
    private final SpringAiChatClientFactory clientFactory;

    public ReviewAgentModelRegistry(AiReviewProperties properties,
                                    ModelConnectionService connectionService,
                                    SpringAiChatClientFactory clientFactory) {
        this.properties = properties;
        this.connectionService = connectionService;
        this.clientFactory = clientFactory;
    }

    public ReviewAgentModel current() {
        ModelConnectionSnapshot connection = connectionService.requireActiveConnection();
        return create(connection);
    }

    public ReviewAgentModel current(String expectedProvider, String expectedModel) {
        ModelConnectionSnapshot connection = connectionService.requireActiveConnection();
        if (!connection.provider().equals(expectedProvider) || !connection.model().equals(expectedModel)) {
            throw new AiReviewException("模型连接已切换，请重新发起审查");
        }
        return create(connection);
    }

    private ReviewAgentModel create(ModelConnectionSnapshot connection) {
        return new SpringAiReviewAgentModel(connection, properties, clientFactory);
    }
}
