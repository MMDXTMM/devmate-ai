package com.devmate.review.model;

import java.util.List;

public record GitChangedFile(
        String oldPath,
        String newPath,
        String changeType,
        int additions,
        int deletions,
        List<LineRange> baseLineRanges,
        List<LineRange> targetLineRanges
) {
    public GitChangedFile {
        baseLineRanges = List.copyOf(baseLineRanges);
        targetLineRanges = List.copyOf(targetLineRanges);
    }
}
