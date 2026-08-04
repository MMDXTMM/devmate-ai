package com.devmate.review.model;

import java.util.List;

public record GitDiffResult(
        String baseRevision,
        String targetRevision,
        List<GitChangedFile> files
) {
    public GitDiffResult {
        files = List.copyOf(files);
    }
}
