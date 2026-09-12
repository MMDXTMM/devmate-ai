package com.devmate.conversation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateConversationMessageRequest(
        @NotBlank(message = "问题不能为空")
        @Size(max = 4000, message = "问题不能超过4000个字符")
        String question,
        @NotBlank(message = "请求标识不能为空")
        @Pattern(regexp = "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
                message = "请求标识必须是小写UUID v4")
        String attemptKey
) { }

