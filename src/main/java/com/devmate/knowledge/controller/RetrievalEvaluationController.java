package com.devmate.knowledge.controller;

import com.devmate.common.api.ApiResponse;
import com.devmate.knowledge.dto.CreateRetrievalEvaluationCaseRequest;
import com.devmate.knowledge.dto.RetrievalEvaluationCaseResponse;
import com.devmate.knowledge.dto.RetrievalEvaluationRunResponse;
import com.devmate.knowledge.dto.RunRetrievalEvaluationRequest;
import com.devmate.knowledge.service.RetrievalEvaluationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "检索评测", description = "管理固定检索样本并计算 Recall、Precision、HitRate 和 MRR")
public class RetrievalEvaluationController {

    private final RetrievalEvaluationService evaluationService;

    public RetrievalEvaluationController(RetrievalEvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @PostMapping("/evaluation-cases")
    @Operation(summary = "创建检索评测用例")
    public ApiResponse<RetrievalEvaluationCaseResponse> createCase(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId,
            @Valid @RequestBody CreateRetrievalEvaluationCaseRequest request
    ) {
        return ApiResponse.success(evaluationService.createCase(projectId, request));
    }

    @GetMapping("/evaluation-cases")
    @Operation(summary = "查询指定版本的检索评测用例")
    public ApiResponse<List<RetrievalEvaluationCaseResponse>> listCases(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId,
            @NotBlank(message = "评测集版本不能为空")
            @Size(max = 64, message = "评测集版本不能超过64个字符")
            @RequestParam String datasetVersion
    ) {
        return ApiResponse.success(evaluationService.listCases(projectId, datasetVersion));
    }

    @PostMapping("/evaluation-runs")
    @Operation(summary = "执行检索评测")
    public ApiResponse<RetrievalEvaluationRunResponse> run(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId,
            @Valid @RequestBody RunRetrievalEvaluationRequest request
    ) {
        return ApiResponse.success(evaluationService.run(
                projectId,
                request.datasetVersion(),
                request.retrievalMode()
        ));
    }

    @GetMapping("/evaluation-runs/latest")
    @Operation(summary = "查询最近一次检索评测结果")
    public ApiResponse<RetrievalEvaluationRunResponse> latest(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId,
            @NotBlank(message = "评测集版本不能为空")
            @Size(max = 64, message = "评测集版本不能超过64个字符")
            @RequestParam String datasetVersion
    ) {
        return ApiResponse.success(evaluationService.latest(projectId, datasetVersion));
    }
}
