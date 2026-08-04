package com.devmate.knowledge.controller;

import com.devmate.common.api.ApiResponse;
import com.devmate.knowledge.dto.EmbeddingIndexTaskResponse;
import com.devmate.knowledge.service.EmbeddingIndexService;
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
public class EmbeddingIndexController {

    private final EmbeddingIndexService embeddingIndexService;

    public EmbeddingIndexController(EmbeddingIndexService embeddingIndexService) {
        this.embeddingIndexService = embeddingIndexService;
    }

    @PostMapping("/index")
    public ApiResponse<EmbeddingIndexTaskResponse> index(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId
    ) {
        return ApiResponse.success(embeddingIndexService.index(projectId));
    }

    @GetMapping("/tasks/latest")
    public ApiResponse<EmbeddingIndexTaskResponse> latest(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId
    ) {
        return ApiResponse.success(embeddingIndexService.latest(projectId));
    }
}
