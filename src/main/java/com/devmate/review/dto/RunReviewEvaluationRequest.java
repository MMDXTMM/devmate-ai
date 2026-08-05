package com.devmate.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RunReviewEvaluationRequest(
        @NotBlank(message = "评测集版本不能为空")
        @Size(max = 64, message = "评测集版本不能超过64个字符")
        @Pattern(regexp = "[A-Za-z0-9._-]+", message = "评测集版本只能包含字母、数字、点、下划线和短横线")
        String datasetVersion,

        @NotNull(message = "AI审查任务ID不能为空")
        @Positive(message = "AI审查任务ID必须大于0")
        Long aiReviewTaskId
) {
}
