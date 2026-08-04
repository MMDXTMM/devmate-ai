package com.devmate.review.controller;

import com.devmate.common.api.ApiResponse;
import com.devmate.knowledge.dto.RetrievalSearchResponse;
import com.devmate.review.dto.ReviewContextRequest;
import com.devmate.review.service.ReviewContextService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/review-diffs")
@Validated
public class ReviewContextController {

    private final ReviewContextService reviewContextService;

    public ReviewContextController(ReviewContextService reviewContextService) {
        this.reviewContextService = reviewContextService;
    }

    @PostMapping("/latest/context")
    public ApiResponse<RetrievalSearchResponse> retrieveLatest(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId,
            @Valid @RequestBody ReviewContextRequest request
    ) {
        return ApiResponse.success(reviewContextService.retrieveLatest(projectId, request));
    }
}
