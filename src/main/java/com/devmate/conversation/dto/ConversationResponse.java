package com.devmate.conversation.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

public record ConversationResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @JsonSerialize(using = ToStringSerializer.class) Long projectId,
        String title,
        String status,
        String revision,
        String provider,
        String modelName,
        String promptVersion,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) { }

