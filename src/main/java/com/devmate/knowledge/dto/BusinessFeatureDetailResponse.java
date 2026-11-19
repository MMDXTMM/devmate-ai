package com.devmate.knowledge.dto;

import java.util.List;

public record BusinessFeatureDetailResponse(
        BusinessFeatureResponse feature,
        String flowSummary,
        List<String> dataOperations,
        List<BusinessCodeEvidenceResponse> implementation,
        List<String> unresolvedCalls
) {
    public BusinessFeatureDetailResponse(BusinessFeatureResponse feature, String flowSummary, List<String> dataOperations,
                                         List<BusinessCodeEvidenceResponse> implementation) {
        this(feature, flowSummary, dataOperations, implementation, List.of());
    }
}
