package com.devmate.knowledge.controller;

import com.devmate.common.api.ApiResponse;
import com.devmate.knowledge.dto.CreateProjectUnderstandingReportRequest;
import com.devmate.knowledge.dto.ProjectUnderstandingReportResponse;
import com.devmate.knowledge.service.ProjectUnderstandingReportService;
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
public class ProjectUnderstandingReportController {
    private final ProjectUnderstandingReportService service;

    public ProjectUnderstandingReportController(ProjectUnderstandingReportService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<ProjectUnderstandingReportResponse> create(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId,
            @Valid @RequestBody CreateProjectUnderstandingReportRequest request
    ) {
        return ApiResponse.success(service.create(projectId, request));
    }

    @GetMapping("/latest")
    public ApiResponse<ProjectUnderstandingReportResponse> latest(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId
    ) {
        return ApiResponse.success(service.latest(projectId));
    }
}
