package com.devmate.review.dto;

import com.devmate.review.entity.ReviewEvaluationCase;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

public record ReviewEvaluationCaseResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @JsonSerialize(using = ToStringSerializer.class) Long projectId,
        @JsonSerialize(using = ToStringSerializer.class) Long reviewTaskId,
        String datasetVersion,
        String caseKey,
        String name,
        String targetRevision,
        String expectationType,
        String category,
        String filePath,
        Integer startLine,
        Integer endLine,
        String rationale,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ReviewEvaluationCaseResponse from(ReviewEvaluationCase value) {
        return new ReviewEvaluationCaseResponse(
                value.getId(), value.getProjectId(), value.getReviewTaskId(),
                value.getDatasetVersion(), value.getCaseKey(), value.getName(),
                value.getTargetRevision(), value.getExpectationType(), value.getCategory(),
                value.getFilePath(), value.getStartLine(), value.getEndLine(),
                value.getRationale(), value.getCreatedAt(), value.getUpdatedAt()
        );
    }
}
