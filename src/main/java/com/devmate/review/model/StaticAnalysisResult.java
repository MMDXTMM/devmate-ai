package com.devmate.review.model;

import java.util.List;

public record StaticAnalysisResult(
        String toolName,
        String toolVersion,
        int analyzedFiles,
        List<StaticFinding> findings
) {
    public StaticAnalysisResult {
        findings = List.copyOf(findings);
    }
}
