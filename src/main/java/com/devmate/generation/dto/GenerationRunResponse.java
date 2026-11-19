package com.devmate.generation.dto;

import java.time.LocalDateTime;
import java.util.List;

public record GenerationRunResponse(String id, String sessionId, String versionId, String status,
                                    String summary, String runInstructions, List<FileEntry> files,
                                    List<String> warnings, String verification, String errorMessage,
                                    LocalDateTime createdAt, LocalDateTime updatedAt) {
    public record FileEntry(String path, int characters) { }
}
