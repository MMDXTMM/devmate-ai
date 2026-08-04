package com.devmate.review.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;
import java.util.List;

public record StaticAnalysisResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @JsonSerialize(using = ToStringSerializer.class) Long projectId,
        @JsonSerialize(using = ToStringSerializer.class) Long reviewTaskId,
        String toolName,
        String toolVersion,
        String status,
        Integer analyzedFiles,
        Integer findingCount,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime finishedAt,
        List<StaticFindingResponse> findings
) {
    public StaticAnalysisResponse {
        findings = List.copyOf(findings);
    }
}
