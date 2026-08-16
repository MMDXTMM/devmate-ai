package com.devmate.knowledge.dto;

import java.util.List;

public record ProjectUnderstandingFlowResponse(
        String name,
        String goal,
        List<String> steps,
        List<String> apiEntries,
        List<String> dataChanges,
        List<ProjectUnderstandingEvidenceResponse> evidence
) { }
