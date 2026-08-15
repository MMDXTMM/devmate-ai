package com.devmate.knowledge.dto;

import java.util.List;

public record BusinessFeatureDetailResponse(
        BusinessFeatureResponse feature,
        String flowSummary,
        List<String> dataOperations,
        List<BusinessCodeEvidenceResponse> implementation
) {
}
