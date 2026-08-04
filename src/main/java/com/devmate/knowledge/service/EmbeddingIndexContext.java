package com.devmate.knowledge.service;

public record EmbeddingIndexContext(
        Long taskId,
        Long projectId,
        String revision,
        String provider,
        String model,
        int dimensions,
        int totalChunks
) {
}
