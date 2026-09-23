package com.devmate.knowledge.controller;

import com.devmate.common.api.ApiResponse;
import com.devmate.knowledge.dto.SourceDocumentResponse;
import com.devmate.knowledge.dto.SourceSymbolDetailResponse;
import com.devmate.knowledge.dto.SourceSymbolResponse;
import com.devmate.knowledge.dto.SourceReferenceResponse;
import com.devmate.knowledge.service.SourceStructureQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/sources")
@Validated
@Tag(name = "源码结构", description = "查询已解析文件、符号、真实代码块和跨符号引用")
public class SourceStructureController {

    private final SourceStructureQueryService queryService;

    public SourceStructureController(SourceStructureQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    @Operation(summary = "查询项目源码文件")
    public ApiResponse<List<SourceDocumentResponse>> listDocuments(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId
    ) {
        return ApiResponse.success(queryService.listDocuments(projectId));
    }

    @GetMapping("/references")
    @Operation(summary = "查询项目符号引用关系")
    public ApiResponse<List<SourceReferenceResponse>> listReferences(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId
    ) {
        return ApiResponse.success(queryService.listReferences(projectId));
    }

    @GetMapping("/{documentId}/symbols")
    @Operation(summary = "查询源码文件中的符号")
    public ApiResponse<List<SourceSymbolResponse>> listSymbols(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId,
            @Positive(message = "源码文件ID必须大于0") @PathVariable Long documentId
    ) {
        return ApiResponse.success(queryService.listSymbols(projectId, documentId));
    }

    @GetMapping("/{documentId}/symbols/{symbolId}")
    @Operation(summary = "查询符号代码详情", description = "返回受长度限制的真实代码块和准确文件行号。")
    public ApiResponse<SourceSymbolDetailResponse> getSymbolDetail(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId,
            @Positive(message = "源码文件ID必须大于0") @PathVariable Long documentId,
            @Positive(message = "源码符号ID必须大于0") @PathVariable Long symbolId
    ) {
        return ApiResponse.success(queryService.getSymbolDetail(projectId, documentId, symbolId));
    }
}
