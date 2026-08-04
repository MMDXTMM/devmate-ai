package com.devmate.agent.model;

import java.util.List;

public record AiReviewModelResult(
        List<AiReviewFinding> findings,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        String finishReason
) {
    public AiReviewModelResult {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }
}
