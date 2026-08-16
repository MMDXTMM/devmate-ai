package com.devmate.knowledge.dto;

public record ProjectReadingStepResponse(
        int order,
        String category,
        String title,
        String reason,
        String filePath,
        String symbolName,
        Integer startLine
) {
}
