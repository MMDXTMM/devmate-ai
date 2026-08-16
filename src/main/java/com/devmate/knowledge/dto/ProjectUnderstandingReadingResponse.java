package com.devmate.knowledge.dto;

import java.util.List;

public record ProjectUnderstandingReadingResponse(
        Integer order,
        String title,
        String reason,
        List<ProjectUnderstandingEvidenceResponse> evidence
) { }
