package com.devmate.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateReviewWorkflowRequest(
        @NotBlank(message = "请求标识不能为空")
        @Pattern(
                regexp = "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}",
                message = "请求标识必须是小写UUID v4"
        )
        String attemptKey
) {
}
