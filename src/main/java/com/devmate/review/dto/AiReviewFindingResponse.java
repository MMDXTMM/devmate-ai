package com.devmate.review.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;

public record AiReviewFindingResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @JsonSerialize(using = ToStringSerializer.class) Long chunkId,
        String source,
        String category,
        String severity,
        String conclusionType,
        BigDecimal confidence,
        String filePath,
        Integer startLine,
        Integer endLine,
        String title,
        String evidence,
        String riskScenario,
        String suggestion,
        String verification
) {
}
