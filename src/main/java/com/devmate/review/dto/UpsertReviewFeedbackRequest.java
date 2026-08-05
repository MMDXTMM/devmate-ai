package com.devmate.review.dto;

import com.devmate.review.model.ReviewFeedbackType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpsertReviewFeedbackRequest(
        @NotNull(message = "反馈类型不能为空") ReviewFeedbackType feedbackType,
        @Size(max = 1000, message = "反馈说明不能超过1000个字符") String comment
) {
}
