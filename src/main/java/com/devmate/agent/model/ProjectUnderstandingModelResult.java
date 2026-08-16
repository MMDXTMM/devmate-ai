package com.devmate.agent.model;

import java.util.List;

public record ProjectUnderstandingModelResult(
        String executiveSummary,
        String architectureNarrative,
        List<BusinessFlow> businessFlows,
        List<ReadingGuide> readingGuide,
        List<String> risksAndUnknowns,
        int promptTokens,
        int completionTokens,
        int totalTokens
) {
    public record BusinessFlow(
            String name,
            String goal,
            List<String> steps,
            List<String> apiEntries,
            List<String> dataChanges,
            List<String> evidenceIds
    ) { }

    public record ReadingGuide(
            Integer order,
            String title,
            String reason,
            List<String> evidenceIds
    ) { }
}
