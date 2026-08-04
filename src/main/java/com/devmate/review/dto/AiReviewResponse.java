package com.devmate.review.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;
import java.util.List;

public record AiReviewResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @JsonSerialize(using = ToStringSerializer.class) Long projectId,
        @JsonSerialize(using = ToStringSerializer.class) Long reviewTaskId,
        @JsonSerialize(using = ToStringSerializer.class) Long staticAnalysisTaskId,
        @JsonSerialize(using = ToStringSerializer.class) Long invocationId,
        String revision,
        String provider,
        String modelName,
        String promptVersion,
        String retrievalConfigVersion,
        String retrievalMode,
        String status,
        Integer contextChunks,
        Integer findingCount,
        Integer rejectedFindings,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        Long latencyMs,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime finishedAt,
        List<AiReviewFindingResponse> findings
) {
    public AiReviewResponse {
        findings = List.copyOf(findings);
    }
}
