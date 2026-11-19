package com.devmate.generation.dto;
import java.time.LocalDateTime;
import java.util.List;
public record RevisionResponse(String id, String runId, String parentId, String kind, String status,
        String request, String summary, String runInstructions, List<GenerationRunResponse.FileEntry> files,
        List<String> warnings, String errorMessage, LocalDateTime createdAt, LocalDateTime updatedAt) { }
