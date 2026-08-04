package com.devmate.review.model;

import java.nio.file.Path;
import java.util.List;

public record StaticAnalysisTarget(
        String relativePath,
        Path sourcePath,
        List<LineRange> changedLines
) {
    public StaticAnalysisTarget {
        sourcePath = sourcePath.toAbsolutePath().normalize();
        changedLines = List.copyOf(changedLines);
    }
}
