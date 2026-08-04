package com.devmate.review.service;

import java.util.List;

public record StaticAnalysisContext(
        Long projectId,
        Long analysisTaskId,
        Long reviewTaskId,
        Long indexTaskId,
        String targetRevision,
        List<StaticAnalysisFileContext> files
) {
    public StaticAnalysisContext {
        files = List.copyOf(files);
    }
}
