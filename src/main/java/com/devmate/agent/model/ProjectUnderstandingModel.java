package com.devmate.agent.model;

public interface ProjectUnderstandingModel {
    String providerName();
    String modelName();
    ProjectUnderstandingModelResult analyze(ProjectUnderstandingPrompt prompt);
}
