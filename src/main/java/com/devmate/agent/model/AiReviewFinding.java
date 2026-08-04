package com.devmate.agent.model;

public record AiReviewFinding(
        String chunkId,
        String category,
        String severity,
        String conclusionType,
        Double confidence,
        String title,
        String evidence,
        String riskScenario,
        String suggestion,
        String verification
) {
}
