package com.devmate.review.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record MappedSymbolResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long chunkId,
        String chunkType,
        String symbolName,
        Integer startLine,
        Integer endLine
) {
}
