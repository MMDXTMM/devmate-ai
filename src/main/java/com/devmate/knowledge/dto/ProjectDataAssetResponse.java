package com.devmate.knowledge.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record ProjectDataAssetResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long chunkId,
        String name,
        String filePath,
        Integer startLine,
        Integer endLine
) {
}
