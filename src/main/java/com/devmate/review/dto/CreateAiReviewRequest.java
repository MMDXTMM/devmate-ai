package com.devmate.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record CreateAiReviewRequest(
        @NotNull(message = "Diff任务ID不能为空")
        @Positive(message = "Diff任务ID必须大于0")
        Long reviewTaskId,

        @NotBlank(message = "目标版本不能为空")
        @Pattern(regexp = "[0-9a-f]{40}", message = "目标版本必须是40位小写Git提交哈希")
        String revision,

        @NotBlank(message = "请求标识不能为空")
        @Pattern(
                regexp = "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}",
                message = "请求标识必须是小写UUID v4"
        )
        String attemptKey
) {
}
