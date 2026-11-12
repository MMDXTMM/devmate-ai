package com.devmate.conversation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record CreateConversationRequest(
        @Size(max = 100, message = "对话标题不能超过100个字符") String title,
        @Size(max = 32) @Schema(description = "可选，使用当前账号已保存连接的提供方；需与 model 同时指定") String provider,
        @Size(max = 100) @Schema(description = "可选，提供方白名单中的模型 ID") String model
) {
    public CreateConversationRequest(String title) { this(title, null, null); }
}
