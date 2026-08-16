package com.devmate.review.service;

import com.devmate.review.entity.CodeReviewTask;
import com.devmate.review.entity.ReviewFinding;

import java.util.List;

public record AiReviewContext(
        Long projectId,
        Long aiReviewTaskId,
        Long invocationId,
        Long staticAnalysisTaskId,
        String provider,
        String modelName,
        CodeReviewTask reviewTask,
        List<ReviewFinding> staticFindings
) {
    public AiReviewContext {
        staticFindings = List.copyOf(staticFindings);
    }
}
