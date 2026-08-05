package com.devmate.agent.model;

import java.util.List;

public interface ReviewAgentModel {

    String providerName();

    String modelName();

    ReviewAgentTurn next(
            List<ReviewAgentMessage> messages,
            List<ReviewAgentToolDefinition> tools
    );
}
