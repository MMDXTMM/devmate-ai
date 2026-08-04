package com.devmate.knowledge.controller;

import com.devmate.common.api.ApiResponse;
import com.devmate.knowledge.dto.IndexTaskResponse;
import com.devmate.knowledge.service.SourceImportService;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/imports")
@Validated
public class SourceImportController {

    private final SourceImportService sourceImportService;

    public SourceImportController(SourceImportService sourceImportService) {
        this.sourceImportService = sourceImportService;
    }

    @PostMapping
    public ApiResponse<IndexTaskResponse> importSource(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId
    ) {
        return ApiResponse.success(sourceImportService.importSource(projectId));
    }

    @GetMapping("/latest")
    public ApiResponse<IndexTaskResponse> getLatestTask(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId
    ) {
        return ApiResponse.success(sourceImportService.getLatestTask(projectId));
    }
}
