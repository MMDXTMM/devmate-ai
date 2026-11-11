package com.devmate.conversation.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

public record ConversationMessageResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @JsonSerialize(using = ToStringSerializer.class) Long conversationId,
        int sequenceNo,
        String role,
        String content,
        @Schema(allowableValues = {"RUNNING", "COMPLETED", "FAILED", "CANCELLED"}) String status,
        String modelName,
        List<ConversationEvidenceResponse> evidence,
        String errorMessage,
        Long latencyMs,
        LocalDateTime createdAt
) { }

