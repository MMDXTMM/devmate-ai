package com.devmate.tool.model;

import com.devmate.knowledge.dto.RetrievalSearchResponse;

public record AgentToolResult(
        boolean succeeded,
        String content,
        String resultSummary,
        RetrievalSearchResponse retrieval
) {
    public static AgentToolResult success(
            String content,
            String resultSummary,
            RetrievalSearchResponse retrieval
    ) {
        return new AgentToolResult(true, content, resultSummary, retrieval);
    }

    public static AgentToolResult failure(String content, String resultSummary) {
        return new AgentToolResult(false, content, resultSummary, null);
    }
}
