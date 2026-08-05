package com.devmate.review.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record ReviewEvaluationItemResultResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long expectedCaseId,
        @JsonSerialize(using = ToStringSerializer.class) Long findingId,
        String outcome,
        String reason
) {
}
