package com.devmate.knowledge.controller;

import com.devmate.common.api.ApiResponse;
import com.devmate.knowledge.dto.EmbeddingIndexTaskResponse;
import com.devmate.knowledge.service.EmbeddingIndexService;
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
@RequestMapping("/api/projects/{projectId}/embeddings")
@Validated
@Tag(name = "向量索引", description = "为当前源码版本建立或复用代码向量，供 Hybrid RAG 检索")
public class EmbeddingIndexController {

    private final EmbeddingIndexService embeddingIndexService;

    public EmbeddingIndexController(EmbeddingIndexService embeddingIndexService) {
        this.embeddingIndexService = embeddingIndexService;
    }

    @PostMapping("/index")
    @Operation(summary = "建立当前版本向量索引", description = "受 Chunk 数量和外部模型超时限制。")
    public ApiResponse<EmbeddingIndexTaskResponse> index(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId
    ) {
        return ApiResponse.success(embeddingIndexService.index(projectId));
    }

    @GetMapping("/tasks/latest")
    @Operation(summary = "查询最近一次向量索引任务")
    public ApiResponse<EmbeddingIndexTaskResponse> latest(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId
    ) {
        return ApiResponse.success(embeddingIndexService.latest(projectId));
    }
}
