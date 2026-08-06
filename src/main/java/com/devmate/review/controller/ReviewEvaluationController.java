package com.devmate.review.controller;

import com.devmate.common.api.ApiResponse;
import com.devmate.review.dto.CreateReviewEvaluationCaseRequest;
import com.devmate.review.dto.ReviewEvaluationCaseResponse;
import com.devmate.review.dto.ReviewEvaluationRunResponse;
import com.devmate.review.dto.RunReviewEvaluationRequest;
import com.devmate.review.service.ReviewEvaluationCaseService;
import com.devmate.review.service.ReviewEvaluationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}")
@Validated
public class ReviewEvaluationController {

    private final ReviewEvaluationCaseService caseService;
    private final ReviewEvaluationService evaluationService;

    public ReviewEvaluationController(
            ReviewEvaluationCaseService caseService,
            ReviewEvaluationService evaluationService
    ) {
        this.caseService = caseService;
        this.evaluationService = evaluationService;
    }

    @PostMapping("/review-evaluation-cases")
    public ApiResponse<ReviewEvaluationCaseResponse> createCase(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId,
            @Valid @RequestBody CreateReviewEvaluationCaseRequest request
    ) {
        return ApiResponse.success(caseService.create(projectId, request));
    }

    @GetMapping("/review-evaluation-cases")
    public ApiResponse<List<ReviewEvaluationCaseResponse>> listCases(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId,
            @NotBlank(message = "评测集版本不能为空")
            @Size(max = 64, message = "评测集版本不能超过64个字符")
            @Pattern(
                    regexp = "[A-Za-z0-9._-]+",
                    message = "评测集版本只能包含字母、数字、点、下划线和短横线"
            )
            @RequestParam String datasetVersion,
            @Positive(message = "Diff任务ID必须大于0")
            @RequestParam(required = false) Long reviewTaskId
    ) {
        return ApiResponse.success(caseService.list(projectId, datasetVersion, reviewTaskId));
    }

    @PostMapping("/review-evaluation-runs")
    public ApiResponse<ReviewEvaluationRunResponse> run(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId,
            @Valid @RequestBody RunReviewEvaluationRequest request
    ) {
        return ApiResponse.success(evaluationService.run(projectId, request));
    }

    @GetMapping("/review-evaluation-runs")
    public ApiResponse<List<ReviewEvaluationRunResponse>> listRuns(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId,
            @NotBlank(message = "评测集版本不能为空")
            @Size(max = 64, message = "评测集版本不能超过64个字符")
            @Pattern(
                    regexp = "[A-Za-z0-9._-]+",
                    message = "评测集版本只能包含字母、数字、点、下划线和短横线"
            )
            @RequestParam String datasetVersion,
            @Positive(message = "Diff任务ID必须大于0") @RequestParam Long reviewTaskId
    ) {
        return ApiResponse.success(evaluationService.list(projectId, datasetVersion, reviewTaskId));
    }
}
