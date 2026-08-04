package com.devmate.review.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;
import java.util.List;

public record ReviewDiffResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @JsonSerialize(using = ToStringSerializer.class) Long projectId,
        String baseRevision,
        String targetRevision,
        String status,
        Integer changedFiles,
        Integer fullyMappedFiles,
        Integer partiallyMappedFiles,
        Integer skippedFiles,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime finishedAt,
        List<ReviewFileResponse> files
) {
}
