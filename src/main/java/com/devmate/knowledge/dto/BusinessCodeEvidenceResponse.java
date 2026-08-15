package com.devmate.knowledge.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record BusinessCodeEvidenceResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long chunkId,
        @JsonSerialize(using = ToStringSerializer.class) Long documentId,
        String layer,
        String symbolName,
        String filePath,
        Integer startLine,
        Integer endLine,
        String explanation,
        String code,
        boolean truncated,
        int originalCharacters
) {
}
