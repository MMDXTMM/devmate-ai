package com.devmate.review.controller;

import com.devmate.common.api.ApiResponse;
import com.devmate.review.dto.CreateReviewDiffRequest;
import com.devmate.review.dto.ReviewDiffResponse;
import com.devmate.review.service.ReviewDiffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@RequestMapping("/api/projects/{projectId}/review-diffs")
@Validated
@Tag(name = "Git Diff", description = "解析两个 Git 版本之间的变更文件、行号、符号与覆盖范围")
public class ReviewDiffController {

    private final ReviewDiffService reviewDiffService;

    public ReviewDiffController(ReviewDiffService reviewDiffService) {
        this.reviewDiffService = reviewDiffService;
    }

    @PostMapping
    @Operation(summary = "创建代码变更任务", description = "Diff 限定审查范围，但后续审查仍会补充完整上下文。")
    public ApiResponse<ReviewDiffResponse> create(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId,
            @Valid @RequestBody CreateReviewDiffRequest request
    ) {
        return ApiResponse.success(reviewDiffService.create(projectId, request));
    }

    @GetMapping("/latest")
    @Operation(summary = "查询最近一次代码变更")
    public ApiResponse<ReviewDiffResponse> getLatest(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId
    ) {
        return ApiResponse.success(reviewDiffService.getLatest(projectId));
    }
}
