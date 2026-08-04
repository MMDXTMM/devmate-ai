package com.devmate.review.model;

import java.util.List;

public record GitChangedFile(
        String oldPath,
        String newPath,
        String changeType,
        int additions,
        int deletions,
        List<LineRange> targetLineRanges
) {
    public GitChangedFile {
        targetLineRanges = List.copyOf(targetLineRanges);
    }
}
