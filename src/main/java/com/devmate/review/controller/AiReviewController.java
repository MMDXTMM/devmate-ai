package com.devmate.review.controller;

import com.devmate.common.api.ApiResponse;
import com.devmate.review.dto.CreateAiReviewRequest;
import com.devmate.review.dto.AiReviewResponse;
import com.devmate.review.service.AiReviewService;
import com.devmate.review.service.AgentAiReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/ai-reviews")
@Validated
public class AiReviewController {

    private final AiReviewService aiReviewService;
    private final AgentAiReviewService agentAiReviewService;

    public AiReviewController(AiReviewService aiReviewService, AgentAiReviewService agentAiReviewService) {
        this.aiReviewService = aiReviewService;
        this.agentAiReviewService = agentAiReviewService;
    }

    @PostMapping("/agent")
    public ApiResponse<AiReviewResponse> createWithAgent(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId,
            @Valid @RequestBody CreateAiReviewRequest request
    ) {
        return ApiResponse.success(agentAiReviewService.create(projectId, request));
    }

    @PostMapping
    public ApiResponse<AiReviewResponse> create(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId,
            @Valid @RequestBody CreateAiReviewRequest request
    ) {
        return ApiResponse.success(aiReviewService.create(projectId, request));
    }

    @GetMapping("/latest")
    public ApiResponse<AiReviewResponse> getLatest(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId
    ) {
        return ApiResponse.success(aiReviewService.getLatest(projectId));
    }

    @GetMapping("/attempts/{attemptKey}")
    public ApiResponse<AiReviewResponse> getByAttemptKey(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId,
            @Pattern(
                    regexp = "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}",
                    message = "请求标识必须是小写UUID v4"
            )
            @PathVariable String attemptKey
    ) {
        return ApiResponse.success(aiReviewService.getByAttemptKey(projectId, attemptKey));
    }
}
