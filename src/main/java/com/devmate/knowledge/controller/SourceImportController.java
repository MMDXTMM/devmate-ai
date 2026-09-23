package com.devmate.knowledge.controller;

import com.devmate.common.api.ApiResponse;
import com.devmate.knowledge.dto.IndexTaskResponse;
import com.devmate.knowledge.service.SourceImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "源码导入", description = "安全克隆、扫描和解析 Java 项目，保存可恢复的索引任务状态")
public class SourceImportController {

    private final SourceImportService sourceImportService;

    public SourceImportController(SourceImportService sourceImportService) {
        this.sourceImportService = sourceImportService;
    }

    @PostMapping
    @Operation(summary = "增量导入项目源码", description = "相同版本会复用未变化的文件和结构化结果。")
    public ApiResponse<IndexTaskResponse> importSource(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId
    ) {
        return ApiResponse.success(sourceImportService.importSource(projectId));
    }

    @PostMapping("/rebuild")
    @Operation(summary = "强制重建项目源码索引", description = "重新解析当前版本，不执行目标仓库中的脚本。")
    public ApiResponse<IndexTaskResponse> rebuildSource(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId
    ) {
        return ApiResponse.success(sourceImportService.rebuildSource(projectId));
    }

    @GetMapping("/latest")
    @Operation(summary = "查询最近一次源码导入任务")
    public ApiResponse<IndexTaskResponse> getLatestTask(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId
    ) {
        return ApiResponse.success(sourceImportService.getLatestTask(projectId));
    }
}
