package com.devmate.conversation.dto;

import jakarta.validation.constraints.Size;

public record CreateConversationRequest(
        @Size(max = 100, message = "对话标题不能超过100个字符") String title
) { }

