package com.devmate.knowledge.controller;

import com.devmate.common.api.ApiResponse;
import com.devmate.knowledge.dto.RetrievalSearchRequest;
import com.devmate.knowledge.dto.RetrievalSearchResponse;
import com.devmate.knowledge.retrieval.ContextRetrievalService;
import com.devmate.knowledge.retrieval.RetrievalSearchCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/retrieval")
@Validated
@Tag(name = "代码检索", description = "按项目和版本隔离的词法、关系图与向量混合检索")
public class RetrievalController {

    private final ContextRetrievalService retrievalService;

    public RetrievalController(ContextRetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @PostMapping("/search")
    @Operation(summary = "检索代码上下文", description = "返回排序依据、Token 裁剪信息和可追溯代码证据。")
    public ApiResponse<RetrievalSearchResponse> search(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId,
            @Valid @RequestBody RetrievalSearchRequest request
    ) {
        return ApiResponse.success(retrievalService.search(
                projectId,
                new RetrievalSearchCommand(
                        request.query(),
                        request.revision(),
                        request.seedChunkIds(),
                        request.topK(),
                        request.tokenBudget(),
                        request.retrievalMode()
                )
        ));
    }
}
