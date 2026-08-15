package com.devmate.generation.dto;

import com.devmate.generation.model.RequirementDecisionMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RequirementAnswerRequest(
        @NotBlank(message = "问题ID不能为空")
        @Size(max = 100, message = "问题ID不能超过100个字符")
        String questionId,
        RequirementDecisionMode decisionMode,
        @Size(max = 4, message = "单个问题最多选择4个选项")
        List<@NotBlank(message = "选项ID不能为空") String> selectedOptionIds,
        @Size(max = 2000, message = "自定义补充不能超过2000个字符")
        String customAnswer,
        @Size(max = 2000, message = "单个答案不能超过2000个字符")
        String answer
) {
}
