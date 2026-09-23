package com.devmate.conversation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "向项目对话发送一轮问题")
public record CreateConversationMessageRequest(
        @Schema(description = "需要基于项目代码回答的问题", example = "订单创建会经过哪些 Service 和数据表？")
        @NotBlank(message = "问题不能为空")
        @Size(max = 4000, message = "问题不能超过4000个字符")
        String question,
        @Schema(description = "本轮提问幂等键；重试必须复用", example = "123e4567-e89b-42d3-a456-426614174000")
        @NotBlank(message = "请求标识不能为空")
        @Pattern(regexp = "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
                message = "请求标识必须是小写UUID v4")
        String attemptKey
) { }
