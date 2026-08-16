package com.devmate.knowledge.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;
import java.util.List;

public record ProjectUnderstandingReportResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @JsonSerialize(using = ToStringSerializer.class) Long projectId,
        String revision,
        String provider,
        String modelName,
        String promptVersion,
        String status,
        String executiveSummary,
        String architectureNarrative,
        List<ProjectUnderstandingFlowResponse> businessFlows,
        List<ProjectUnderstandingReadingResponse> readingGuide,
        List<String> risksAndUnknowns,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        Long latencyMs,
        String errorMessage,
        String attemptKey,
        LocalDateTime createdAt,
        LocalDateTime finishedAt
) { }
