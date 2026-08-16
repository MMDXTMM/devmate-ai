package com.devmate.knowledge.dto;

import java.util.List;

public record ProjectOnboardingResponse(
        String purpose,
        String architectureSummary,
        List<String> detectedCapabilities,
        List<BusinessJourneyResponse> coreJourneys,
        List<ProjectStateModelResponse> stateModels,
        List<ProjectDataAssetResponse> dataAssets,
        List<ProjectReadingStepResponse> readingOrder,
        List<String> unknowns
) {
}
