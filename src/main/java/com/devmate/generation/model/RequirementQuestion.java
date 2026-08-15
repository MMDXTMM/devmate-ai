package com.devmate.generation.model;

import java.util.List;

public record RequirementQuestion(
        String id,
        RequirementQuestionCategory category,
        RequirementInputType inputType,
        String question,
        String reason,
        String aiRecommendation,
        String recommendationReason,
        List<RequirementOption> options,
        boolean required,
        boolean allowCustomAnswer,
        boolean legacy
) {

    public RequirementQuestion {
        legacy = legacy || inputType == null;
        category = category == null ? RequirementQuestionCategory.BUSINESS : category;
        inputType = inputType == null ? RequirementInputType.FREE_TEXT : inputType;
        aiRecommendation = aiRecommendation == null ? "" : aiRecommendation;
        recommendationReason = recommendationReason == null ? "" : recommendationReason;
        options = options == null ? List.of() : List.copyOf(options);
    }

    public RequirementQuestion(String id, String question, String reason, boolean required) {
        this(
                id,
                RequirementQuestionCategory.BUSINESS,
                RequirementInputType.FREE_TEXT,
                question,
                reason,
                "",
                "",
                List.of(),
                required,
                true,
                true
        );
    }
}
