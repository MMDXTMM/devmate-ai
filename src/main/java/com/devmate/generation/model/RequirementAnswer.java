package com.devmate.generation.model;

import java.util.List;

public record RequirementAnswer(
        String questionId,
        RequirementDecisionMode decisionMode,
        List<String> selectedOptionIds,
        String customAnswer,
        String answer
) {

    public RequirementAnswer {
        decisionMode = decisionMode == null ? RequirementDecisionMode.LEGACY_TEXT : decisionMode;
        selectedOptionIds = selectedOptionIds == null ? List.of() : List.copyOf(selectedOptionIds);
        customAnswer = customAnswer == null ? "" : customAnswer;
        answer = answer == null ? "" : answer;
    }

    public RequirementAnswer(String questionId, String answer) {
        this(questionId, RequirementDecisionMode.LEGACY_TEXT, List.of(), answer, answer);
    }
}
