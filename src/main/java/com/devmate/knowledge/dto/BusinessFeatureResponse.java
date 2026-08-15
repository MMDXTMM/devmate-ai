package com.devmate.knowledge.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.util.List;

public record BusinessFeatureResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String name,
        String description,
        List<String> httpMethods,
        String path,
        String controllerSymbol,
        String controllerFilePath,
        Integer startLine,
        Integer endLine,
        int implementationSteps,
        boolean accessesData
) {
}
