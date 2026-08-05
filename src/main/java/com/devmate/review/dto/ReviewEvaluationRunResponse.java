package com.devmate.review.dto;

import com.devmate.review.entity.ReviewEvaluationRun;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ReviewEvaluationRunResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @JsonSerialize(using = ToStringSerializer.class) Long projectId,
        @JsonSerialize(using = ToStringSerializer.class) Long reviewTaskId,
        @JsonSerialize(using = ToStringSerializer.class) Long aiReviewTaskId,
        String datasetVersion,
        String datasetHash,
        String executionMode,
        String revision,
        String modelName,
        String promptVersion,
        String retrievalConfigVersion,
        String status,
        Integer expectedDefects,
        Integer predictedFindings,
        Integer truePositives,
        Integer falsePositives,
        Integer falseNegatives,
        Integer manualReviewCount,
        boolean partialMetrics,
        BigDecimal precision,
        BigDecimal recall,
        BigDecimal f1,
        Integer totalTokens,
        Long latencyMs,
        Integer toolCallCount,
        Integer toolSuccessCount,
        LocalDateTime createdAt,
        LocalDateTime finishedAt,
        List<ReviewEvaluationItemResultResponse> results
) {
    public ReviewEvaluationRunResponse {
        results = List.copyOf(results);
    }

    public static ReviewEvaluationRunResponse from(
            ReviewEvaluationRun value,
            List<ReviewEvaluationItemResultResponse> results
    ) {
        return new ReviewEvaluationRunResponse(
                value.getId(), value.getProjectId(), value.getReviewTaskId(),
                value.getAiReviewTaskId(), value.getDatasetVersion(), value.getDatasetHash(),
                value.getExecutionMode(), value.getRevision(), value.getModelName(),
                value.getPromptVersion(), value.getRetrievalConfigVersion(), value.getStatus(),
                value.getExpectedDefects(), value.getPredictedFindings(), value.getTruePositives(),
                value.getFalsePositives(), value.getFalseNegatives(), value.getManualReviewCount(),
                value.getPartialMetrics() != null && value.getPartialMetrics() == 1,
                value.getPrecisionScore(), value.getRecallScore(), value.getF1Score(),
                value.getTotalTokens(), value.getLatencyMs(), value.getToolCallCount(),
                value.getToolSuccessCount(), value.getCreatedAt(), value.getFinishedAt(), results
        );
    }
}
