package com.devmate.knowledge.controller;

import com.devmate.common.api.ApiResponse;
import com.devmate.knowledge.dto.CreateRetrievalEvaluationCaseRequest;
import com.devmate.knowledge.dto.RetrievalEvaluationCaseResponse;
import com.devmate.knowledge.dto.RetrievalEvaluationRunResponse;
import com.devmate.knowledge.dto.RunRetrievalEvaluationRequest;
import com.devmate.knowledge.service.RetrievalEvaluationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
@RequestMapping("/api/projects/{projectId}/retrieval")
@Validated
public class RetrievalEvaluationController {

    private final RetrievalEvaluationService evaluationService;

    public RetrievalEvaluationController(RetrievalEvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @PostMapping("/evaluation-cases")
    public ApiResponse<RetrievalEvaluationCaseResponse> createCase(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId,
            @Valid @RequestBody CreateRetrievalEvaluationCaseRequest request
    ) {
        return ApiResponse.success(evaluationService.createCase(projectId, request));
    }

    @GetMapping("/evaluation-cases")
    public ApiResponse<List<RetrievalEvaluationCaseResponse>> listCases(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId,
            @NotBlank(message = "评测集版本不能为空")
            @Size(max = 64, message = "评测集版本不能超过64个字符")
            @RequestParam String datasetVersion
    ) {
        return ApiResponse.success(evaluationService.listCases(projectId, datasetVersion));
    }

    @PostMapping("/evaluation-runs")
    public ApiResponse<RetrievalEvaluationRunResponse> run(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId,
            @Valid @RequestBody RunRetrievalEvaluationRequest request
    ) {
        return ApiResponse.success(evaluationService.run(projectId, request.datasetVersion()));
    }

    @GetMapping("/evaluation-runs/latest")
    public ApiResponse<RetrievalEvaluationRunResponse> latest(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId,
            @NotBlank(message = "评测集版本不能为空")
            @Size(max = 64, message = "评测集版本不能超过64个字符")
            @RequestParam String datasetVersion
    ) {
        return ApiResponse.success(evaluationService.latest(projectId, datasetVersion));
    }
}
