package com.devmate.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "生成中文项目深层理解报告")
public record CreateProjectUnderstandingReportRequest(
        @Schema(description = "报告绑定的完整 Git revision", example = "0123456789abcdef0123456789abcdef01234567")
        @NotBlank(message = "项目版本不能为空")
        @Pattern(regexp = "[0-9a-f]{40}", message = "项目版本必须是40位小写Git提交哈希")
        String revision,

        @Schema(description = "本次生成幂等键；重试必须复用", example = "123e4567-e89b-42d3-a456-426614174000")
        @NotBlank(message = "请求标识不能为空")
        @Pattern(
                regexp = "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
                message = "请求标识必须是小写UUID v4"
        )
        String attemptKey
) { }
