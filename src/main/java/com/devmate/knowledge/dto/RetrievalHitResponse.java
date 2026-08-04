package com.devmate.knowledge.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.util.List;

public record RetrievalHitResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long chunkId,
        @JsonSerialize(using = ToStringSerializer.class) Long documentId,
        String filePath,
        String sourceKind,
        String chunkType,
        String symbolName,
        Integer startLine,
        Integer endLine,
        double score,
        int estimatedTokens,
        List<String> reasons,
        String excerpt
) {
}
