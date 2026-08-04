package com.devmate.knowledge.dto;

import com.devmate.knowledge.entity.IndexTask;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

public record IndexTaskResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @JsonSerialize(using = ToStringSerializer.class) Long projectId,
        String taskType,
        String revision,
        String status,
        Integer totalFiles,
        Integer processedFiles,
        Integer failedFiles,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {
    public static IndexTaskResponse from(IndexTask task) {
        return new IndexTaskResponse(
                task.getId(), task.getProjectId(), task.getTaskType(), task.getRevision(),
                task.getStatus(), task.getTotalFiles(), task.getProcessedFiles(),
                task.getFailedFiles(), task.getErrorMessage(), task.getCreatedAt(),
                task.getStartedAt(), task.getFinishedAt()
        );
    }
}
