package com.devmate.knowledge.retrieval;

import java.util.List;

public record RetrievalSearchCommand(
        String query,
        String revision,
        List<Long> seedChunkIds,
        Integer topK,
        Integer tokenBudget
) {
    public RetrievalSearchCommand {
        seedChunkIds = seedChunkIds == null ? List.of() : List.copyOf(seedChunkIds);
    }
}
