package com.devmate.conversation.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;
import java.util.List;

public record ConversationMessageResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @JsonSerialize(using = ToStringSerializer.class) Long conversationId,
        int sequenceNo,
        String role,
        String content,
        String status,
        String modelName,
        List<ConversationEvidenceResponse> evidence,
        String errorMessage,
        Long latencyMs,
        LocalDateTime createdAt
) { }

