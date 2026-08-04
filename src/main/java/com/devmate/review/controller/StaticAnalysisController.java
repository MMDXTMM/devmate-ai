package com.devmate.review.controller;

import com.devmate.common.api.ApiResponse;
import com.devmate.review.dto.StaticAnalysisResponse;
import com.devmate.review.service.StaticAnalysisService;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/static-analyses")
@Validated
public class StaticAnalysisController {

    private final StaticAnalysisService staticAnalysisService;

    public StaticAnalysisController(StaticAnalysisService staticAnalysisService) {
        this.staticAnalysisService = staticAnalysisService;
    }

    @PostMapping
    public ApiResponse<StaticAnalysisResponse> create(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId
    ) {
        return ApiResponse.success(staticAnalysisService.create(projectId));
    }

    @GetMapping("/latest")
    public ApiResponse<StaticAnalysisResponse> getLatest(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId
    ) {
        return ApiResponse.success(staticAnalysisService.getLatest(projectId));
    }
}
