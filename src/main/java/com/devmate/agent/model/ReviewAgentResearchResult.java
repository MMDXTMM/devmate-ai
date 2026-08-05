package com.devmate.agent.model;

import com.devmate.knowledge.dto.RetrievalSearchResponse;

public record ReviewAgentResearchResult(
        RetrievalSearchResponse retrieval,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        int toolCallCount
) {
}
