package com.devmate.conversation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "重命名当前账号的项目会话")
public record RenameConversationRequest(
        @NotBlank @Size(max = 100)
        @Schema(description = "新标题，去除首尾空白后 1–100 字", maxLength = 100) String title
) { }
