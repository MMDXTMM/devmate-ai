package com.devmate.knowledge.controller;

import com.devmate.common.api.ApiResponse;
import com.devmate.knowledge.dto.CreateProjectUnderstandingReportRequest;
import com.devmate.knowledge.dto.ProjectUnderstandingReportResponse;
import com.devmate.knowledge.service.ProjectUnderstandingReportService;
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
@RequestMapping("/api/projects/{projectId}/understanding-reports")
@Validated
@Tag(name = "AI 项目理解报告", description = "基于静态业务地图和白名单代码证据生成中文深层理解报告")
public class ProjectUnderstandingReportController {
    private final ProjectUnderstandingReportService service;

    public ProjectUnderstandingReportController(ProjectUnderstandingReportService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "生成 AI 项目理解报告", description = "attemptKey 保证一次点击幂等；调用账户当前模型并产生费用。")
    public ApiResponse<ProjectUnderstandingReportResponse> create(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId,
            @Valid @RequestBody CreateProjectUnderstandingReportRequest request
    ) {
        return ApiResponse.success(service.create(projectId, request));
    }

    @GetMapping("/latest")
    @Operation(summary = "查询最近一份项目理解报告")
    public ApiResponse<ProjectUnderstandingReportResponse> latest(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId
    ) {
        return ApiResponse.success(service.latest(projectId));
    }
}
