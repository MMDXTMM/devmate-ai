package com.devmate.review.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record StaticFindingResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String source,
        String ruleId,
        String category,
        String severity,
        String filePath,
        Integer startLine,
        Integer endLine,
        String message,
        String evidence
) {
}
