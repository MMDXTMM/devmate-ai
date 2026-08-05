package com.devmate.tool.model;

public record ReviewAgentContext(
        Long projectId,
        Long invocationId,
        Long reviewTaskId,
        Long staticAnalysisTaskId,
        String revision
) {
}
