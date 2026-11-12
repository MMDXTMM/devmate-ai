package com.devmate.conversation.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "停止请求受理结果；消息最终状态以 SSE done 或历史为准")
public record ConversationCancellationResponse(
        @JsonSerialize(using = ToStringSerializer.class) @Schema(type = "string") Long messageId,
        boolean cancellationRequested
) { }
