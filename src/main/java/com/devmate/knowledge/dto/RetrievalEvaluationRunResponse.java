package com.devmate.knowledge.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;
import java.util.List;

public record RetrievalEvaluationRunResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @JsonSerialize(using = ToStringSerializer.class) Long projectId,
        String revision,
        String datasetVersion,
        String retrievalConfigVersion,
        String retrievalMode,
        String status,
        int totalCases,
        int resolvedCases,
        double recallAtK,
        double precisionAtK,
        double hitRateAtK,
        double meanReciprocalRank,
        LocalDateTime createdAt,
        LocalDateTime finishedAt,
        List<RetrievalEvaluationCaseResultResponse> cases
) {
}
