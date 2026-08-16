package com.devmate.knowledge.dto;

import java.util.List;

public record BusinessJourneyResponse(
        String moduleId,
        String name,
        String goal,
        List<String> apiEntries,
        List<String> implementationFlow,
        List<String> dataOperations,
        List<String> failureSignals,
        List<String> evidenceFiles
) {
}
