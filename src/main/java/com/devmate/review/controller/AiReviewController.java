package com.devmate.review.controller;

import com.devmate.common.api.ApiResponse;
import com.devmate.review.dto.AiReviewResponse;
import com.devmate.review.service.AiReviewService;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/ai-reviews")
@Validated
public class AiReviewController {

    private final AiReviewService aiReviewService;

    public AiReviewController(AiReviewService aiReviewService) {
        this.aiReviewService = aiReviewService;
    }

    @PostMapping
    public ApiResponse<AiReviewResponse> create(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId
    ) {
        return ApiResponse.success(aiReviewService.create(projectId));
    }

    @GetMapping("/latest")
    public ApiResponse<AiReviewResponse> getLatest(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId
    ) {
        return ApiResponse.success(aiReviewService.getLatest(projectId));
    }
}
