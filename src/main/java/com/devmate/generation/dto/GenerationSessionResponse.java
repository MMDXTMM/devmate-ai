package com.devmate.generation.dto;

import java.time.LocalDateTime;

public record GenerationSessionResponse(
        String id,
        String originalRequirement,
        String status,
        int latestVersionNo,
        String confirmedVersionId,
        GenerationSpecResponse latestSpec,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
