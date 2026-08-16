package com.devmate.knowledge.dto;

public record ProjectUnderstandingEvidenceResponse(
        String chunkId,
        String symbolName,
        String filePath,
        Integer startLine,
        Integer endLine,
        String code,
        boolean truncated
) { }
