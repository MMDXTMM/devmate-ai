package com.devmate.review.service;

import com.devmate.review.model.LineRange;

import java.util.List;

public record StaticAnalysisFileContext(
        String relativePath,
        List<LineRange> changedLines
) {
    public StaticAnalysisFileContext {
        changedLines = List.copyOf(changedLines);
    }
}
