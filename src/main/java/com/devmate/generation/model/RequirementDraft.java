package com.devmate.generation.model;

import java.util.List;

public record RequirementDraft(
        String requirementSummary,
        String architectureSummary,
        List<String> assumptions,
        List<RequirementQuestion> questions,
        String promptVersion
) {
}
