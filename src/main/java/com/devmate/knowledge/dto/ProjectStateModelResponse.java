package com.devmate.knowledge.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.util.List;

public record ProjectStateModelResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long chunkId,
        String name,
        List<String> values,
        String filePath,
        Integer startLine,
        Integer endLine
) {
}
