package com.devmate.generation.dto;

import com.devmate.generation.model.RequirementAnswer;
import com.devmate.generation.model.RequirementQuestion;

import java.time.LocalDateTime;
import java.util.List;

public record GenerationSpecResponse(
        String id,
        int versionNo,
        String requirementSummary,
        String architectureSummary,
        List<String> assumptions,
        List<RequirementQuestion> questions,
        List<RequirementAnswer> answers,
        String status,
        String promptVersion,
        LocalDateTime createdAt
) {
}
