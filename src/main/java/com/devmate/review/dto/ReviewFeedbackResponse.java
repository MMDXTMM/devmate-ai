package com.devmate.review.dto;

import com.devmate.review.entity.CodeReviewFeedback;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

public record ReviewFeedbackResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @JsonSerialize(using = ToStringSerializer.class) Long projectId,
        @JsonSerialize(using = ToStringSerializer.class) Long findingId,
        String feedbackType,
        String comment,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ReviewFeedbackResponse from(CodeReviewFeedback feedback) {
        return new ReviewFeedbackResponse(
                feedback.getId(), feedback.getProjectId(), feedback.getFindingId(),
                feedback.getFeedbackType(), feedback.getComment(), feedback.getCreatedAt(),
                feedback.getUpdatedAt()
        );
    }
}
