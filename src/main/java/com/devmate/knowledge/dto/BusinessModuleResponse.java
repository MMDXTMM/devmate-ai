package com.devmate.knowledge.dto;

import java.util.List;

public record BusinessModuleResponse(
        String id,
        String name,
        String description,
        String controllerSymbol,
        String controllerFilePath,
        Integer startLine,
        Integer endLine,
        List<BusinessFeatureResponse> features
) {
}
