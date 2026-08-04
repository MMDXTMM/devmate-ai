package com.devmate.review.service;

public record ReviewDiffContext(
        Long projectId,
        Long reviewTaskId,
        Long indexTaskId
) {
}
