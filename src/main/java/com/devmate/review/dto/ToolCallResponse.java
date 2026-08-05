package com.devmate.review.dto;

import com.devmate.tool.entity.ToolCallLog;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

public record ToolCallResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String toolCallId,
        Integer stepNo,
        String toolName,
        String argumentsSummary,
        String resultSummary,
        String status,
        Long latencyMs,
        String errorMessage,
        LocalDateTime createdAt
) {
    public static ToolCallResponse from(ToolCallLog log) {
        return new ToolCallResponse(
                log.getId(), log.getToolCallId(), log.getStepNo(), log.getToolName(),
                log.getArgumentsSummary(), log.getResultSummary(), log.getStatus(),
                log.getLatencyMs(), log.getErrorMessage(), log.getCreatedAt()
        );
    }
}
