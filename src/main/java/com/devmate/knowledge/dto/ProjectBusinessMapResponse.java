package com.devmate.knowledge.dto;

import java.util.List;

public record ProjectBusinessMapResponse(
        String revision,
        String analysisMode,
        String summary,
        int moduleCount,
        int endpointCount,
        List<BusinessModuleResponse> modules,
        List<String> limitations
) {
}
