package com.devmate.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

@Schema(description = "创建证据约束的 AI 代码审查")
public record CreateAiReviewRequest(
        @Schema(description = "已成功生成的 Diff 任务 ID", type = "string")
        @NotNull(message = "Diff任务ID不能为空")
        @Positive(message = "Diff任务ID必须大于0")
        Long reviewTaskId,

        @Schema(description = "Diff 目标版本的完整 Git revision", example = "0123456789abcdef0123456789abcdef01234567")
        @NotBlank(message = "目标版本不能为空")
        @Pattern(regexp = "[0-9a-f]{40}", message = "目标版本必须是40位小写Git提交哈希")
        String revision,

        @Schema(description = "本次模型调用幂等键；重试必须复用", example = "123e4567-e89b-42d3-a456-426614174000")
        @NotBlank(message = "请求标识不能为空")
        @Pattern(
                regexp = "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
                message = "请求标识必须是小写UUID v4"
        )
        String attemptKey
) {
}
