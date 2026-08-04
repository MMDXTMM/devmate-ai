package com.devmate.agent.model;

public interface AiReviewModel {

    String providerName();

    String modelName();

    AiReviewModelResult review(AiReviewPrompt prompt);
}
