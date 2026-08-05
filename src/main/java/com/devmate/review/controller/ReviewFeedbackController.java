package com.devmate.review.controller;

import com.devmate.common.api.ApiResponse;
import com.devmate.review.dto.ReviewFeedbackResponse;
import com.devmate.review.dto.UpsertReviewFeedbackRequest;
import com.devmate.review.service.ReviewFeedbackService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/review-findings/{findingId}/feedback")
@Validated
public class ReviewFeedbackController {

    private final ReviewFeedbackService reviewFeedbackService;

    public ReviewFeedbackController(ReviewFeedbackService reviewFeedbackService) {
        this.reviewFeedbackService = reviewFeedbackService;
    }

    @PutMapping
    public ApiResponse<ReviewFeedbackResponse> upsert(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId,
            @Positive(message = "结论ID必须大于0") @PathVariable Long findingId,
            @Valid @RequestBody UpsertReviewFeedbackRequest request
    ) {
        return ApiResponse.success(reviewFeedbackService.upsert(projectId, findingId, request));
    }
}
