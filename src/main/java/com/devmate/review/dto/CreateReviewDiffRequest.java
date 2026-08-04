package com.devmate.review.dto;

import jakarta.validation.constraints.Pattern;

public record CreateReviewDiffRequest(
        @Pattern(regexp = "[0-9a-fA-F]{7,40}", message = "基准版本必须是7到40位Git提交哈希")
        String baseRevision,
        @Pattern(regexp = "[0-9a-fA-F]{7,40}", message = "目标版本必须是7到40位Git提交哈希")
        String targetRevision
) {
}
