package com.devmate.conversation.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record ConversationEvidenceResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long chunkId,
        String filePath,
        String symbolName,
        Integer startLine,
        Integer endLine,
        String excerpt
) { }

