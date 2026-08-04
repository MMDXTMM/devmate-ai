package com.devmate.review.model;

import java.util.List;

public record AiFindingValidationResult(
        List<ValidatedAiFinding> findings,
        int rejectedCount
) {
    public AiFindingValidationResult {
        findings = List.copyOf(findings);
    }
}
