package com.devmate.review.dto;

import com.devmate.knowledge.dto.EmbeddingIndexTaskResponse;
import com.devmate.knowledge.dto.IndexTaskResponse;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

public record ReviewWorkflowResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @JsonSerialize(using = ToStringSerializer.class) Long projectId,
        String attemptKey,
        String status,
        String currentStage,
        @JsonSerialize(using = ToStringSerializer.class) Long indexTaskId,
        @JsonSerialize(using = ToStringSerializer.class) Long reviewTaskId,
        @JsonSerialize(using = ToStringSerializer.class) Long staticAnalysisTaskId,
        @JsonSerialize(using = ToStringSerializer.class) Long embeddingTaskId,
        @JsonSerialize(using = ToStringSerializer.class) Long aiReviewTaskId,
        String errorMessage,
        String recoveryAction,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        IndexTaskResponse sourceImport,
        ReviewDiffResponse reviewDiff,
        StaticAnalysisResponse staticAnalysis,
        EmbeddingIndexTaskResponse embeddingIndex,
        AiReviewResponse aiReview
) {
}
