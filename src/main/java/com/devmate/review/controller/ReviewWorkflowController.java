package com.devmate.review.controller;

import com.devmate.common.api.ApiResponse;
import com.devmate.review.dto.CreateReviewWorkflowRequest;
import com.devmate.review.dto.ReviewWorkflowResponse;
import com.devmate.review.service.ReviewWorkflowService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/review-workflows")
@Validated
public class ReviewWorkflowController {

    private final ReviewWorkflowService service;

    public ReviewWorkflowController(ReviewWorkflowService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<ReviewWorkflowResponse> create(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId,
            @Valid @RequestBody CreateReviewWorkflowRequest request
    ) {
        return ApiResponse.success(service.create(projectId, request.attemptKey()));
    }

    @GetMapping("/latest")
    public ApiResponse<ReviewWorkflowResponse> latest(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId
    ) {
        return ApiResponse.success(service.latest(projectId));
    }
}
