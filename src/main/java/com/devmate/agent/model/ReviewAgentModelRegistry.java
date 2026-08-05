package com.devmate.agent.model;

import com.devmate.agent.config.AiReviewProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReviewAgentModelRegistry {

    private final List<ReviewAgentModel> models;
    private final AiReviewProperties properties;

    public ReviewAgentModelRegistry(List<ReviewAgentModel> models, AiReviewProperties properties) {
        this.models = List.copyOf(models);
        this.properties = properties;
    }

    public ReviewAgentModel current() {
        return models.stream()
                .filter(model -> model.providerName().equalsIgnoreCase(properties.getProvider()))
                .findFirst()
                .orElseThrow(() -> new AiReviewException(
                        "未找到AI Agent模型Provider：" + properties.getProvider()
                ));
    }
}
