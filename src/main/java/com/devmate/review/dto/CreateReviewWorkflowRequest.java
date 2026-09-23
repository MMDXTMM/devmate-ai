package com.devmate.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "创建一键代码审查工作流")
public record CreateReviewWorkflowRequest(
        @Schema(
                description = "客户端单次操作的幂等键；响应丢失后必须复用原值，不能生成新键盲目重试",
                example = "123e4567-e89b-42d3-a456-426614174000",
                pattern = "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"
        )
        @NotBlank(message = "请求标识不能为空")
        @Pattern(
                regexp = "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
                message = "请求标识必须是小写UUID v4"
        )
        String attemptKey
) {
}
