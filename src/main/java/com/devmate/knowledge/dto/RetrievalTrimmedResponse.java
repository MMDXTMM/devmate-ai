package com.devmate.knowledge.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record RetrievalTrimmedResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long chunkId,
        String filePath,
        String symbolName,
        int estimatedTokens,
        String reason
) {
}
