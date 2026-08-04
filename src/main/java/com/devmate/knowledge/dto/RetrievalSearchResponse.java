package com.devmate.knowledge.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.util.List;

public record RetrievalSearchResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long projectId,
        String revision,
        String query,
        String configVersion,
        int candidateCount,
        boolean candidateLimitReached,
        boolean referenceLimitReached,
        int topK,
        int tokenBudget,
        int usedTokens,
        int selectedCount,
        int trimmedCount,
        int omittedTrimmedDetails,
        List<RetrievalHitResponse> hits,
        List<RetrievalTrimmedResponse> trimmed
) {
}
