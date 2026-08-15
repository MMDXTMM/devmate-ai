package com.devmate.review.model;

public enum ReviewWorkflowStage {
    SOURCE_IMPORT,
    DIFF,
    STATIC_ANALYSIS,
    EMBEDDING,
    AGENT_REVIEW,
    COMPLETED
}
