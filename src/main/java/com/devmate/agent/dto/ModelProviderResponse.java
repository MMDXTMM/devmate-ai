package com.devmate.agent.dto;

import java.util.List;

public record ModelProviderResponse(
        String provider,
        String displayName,
        String baseUrl,
        List<String> models,
        boolean configured,
        boolean active,
        String selectedModel
) {
}
