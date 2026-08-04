package com.devmate.knowledge.dto;

import com.devmate.knowledge.entity.EmbeddingIndexTask;

import java.time.LocalDateTime;

public record EmbeddingIndexTaskResponse(
        Long id,
        Long projectId,
        String revision,
        String provider,
        String modelName,
        int dimensions,
        String status,
        int totalChunks,
        int processedChunks,
        int skippedChunks,
        int failedChunks,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {
    public static EmbeddingIndexTaskResponse from(EmbeddingIndexTask task) {
        return new EmbeddingIndexTaskResponse(
                task.getId(), task.getProjectId(), task.getRevision(), task.getProvider(),
                task.getModelName(), task.getDimensions(), task.getStatus(), task.getTotalChunks(),
                task.getProcessedChunks(), task.getSkippedChunks(), task.getFailedChunks(),
                task.getErrorMessage(), task.getCreatedAt(), task.getStartedAt(), task.getFinishedAt()
        );
    }
}
