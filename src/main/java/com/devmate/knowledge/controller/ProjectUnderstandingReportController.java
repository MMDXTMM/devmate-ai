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
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/projects/{projectId}/understanding-reports")
@Validated
@Tag(name = "AI 项目理解报告", description = "按固定快照抽样源码、清单、文档与测试，生成分层通俗导览；旧报告兼容回读")
public class ProjectUnderstandingReportController {
    private final ProjectUnderstandingReportService service;

    public ProjectUnderstandingReportController(ProjectUnderstandingReportService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "生成 AI 项目理解报告", description = "attemptKey 保证一次点击幂等；调用账户当前模型并产生费用。background=true 时 HTTP 200 立即返回 RUNNING 任务，通过 GET latest 轮询 SUCCEEDED/FAILED；默认 false 保持同步兼容。队列已满返回 409，后台模型或契约失败通过任务 errorMessage 返回。新报告返回 project-guide-v1 guide 字段，旧报告该字段为空；无权限 403、版本变化/任务并发 409、模型或契约失败 500。")
    public ApiResponse<ProjectUnderstandingReportResponse> create(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId,
            @Valid @RequestBody CreateProjectUnderstandingReportRequest request,
            @io.swagger.v3.oas.annotations.Parameter(description = "true 时立即返回持久化任务，使用 latest 查询状态；默认保留同步兼容")
            @RequestParam(defaultValue = "false") boolean background
    ) {
        return ApiResponse.success(background ? service.createAsync(projectId, request) : service.create(projectId, request));
    }

    @GetMapping("/latest")
    @Operation(summary = "查询当前账户最近一份项目理解报告")
    public ApiResponse<ProjectUnderstandingReportResponse> latest(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId
    ) {
        return ApiResponse.success(service.latest(projectId));
    }
}
