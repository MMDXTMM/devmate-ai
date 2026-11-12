package com.devmate.agent.model;

import java.util.List;
import com.devmate.knowledge.dto.ProjectGuide;

public record ProjectUnderstandingModelResult(
        String executiveSummary,
        String architectureNarrative,
        List<BusinessFlow> businessFlows,
        List<ReadingGuide> readingGuide,
        List<String> risksAndUnknowns,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        ProjectGuide guide
) {
    public ProjectUnderstandingModelResult(String executiveSummary, String architectureNarrative,
            List<BusinessFlow> businessFlows, List<ReadingGuide> readingGuide, List<String> risksAndUnknowns,
            int promptTokens, int completionTokens, int totalTokens) {
        this(executiveSummary, architectureNarrative, businessFlows, readingGuide, risksAndUnknowns,
                promptTokens, completionTokens, totalTokens, null);
    }

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
