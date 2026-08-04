package com.devmate.knowledge.vector;

import java.util.List;

public record VectorSearchResult(
        List<VectorMatch> matches,
        int indexedVectorCount,
        boolean limitReached,
        boolean available,
        String degradationReason
) {

    public VectorSearchResult {
        matches = List.copyOf(matches);
    }

    public static VectorSearchResult unavailable(String reason) {
        return new VectorSearchResult(List.of(), 0, false, false, reason);
    }
}
