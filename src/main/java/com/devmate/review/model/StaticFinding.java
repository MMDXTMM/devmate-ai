package com.devmate.review.model;

public record StaticFinding(
        String ruleId,
        String category,
        String severity,
        String filePath,
        int startLine,
        int endLine,
        String message,
        String evidence
) {
}
