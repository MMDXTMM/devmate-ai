package com.devmate.project.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.devmate.project.entity.Project;

import java.time.LocalDateTime;

public record ProjectResponse(
        @JsonSerialize(using = ToStringSerializer.class)
        Long id,
        String name,
        String description,
        String sourceType,
        String sourceLocation,
        String defaultBranch,
        String currentRevision,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime lastIndexedAt
) {

    public static ProjectResponse from(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getSourceType(),
                project.getSourceLocation(),
                project.getDefaultBranch(),
                project.getCurrentRevision(),
                project.getStatus(),
                project.getCreatedAt(),
                project.getUpdatedAt(),
                project.getLastIndexedAt()
        );
    }
}
