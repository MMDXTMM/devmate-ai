package com.devmate.knowledge.controller;

import com.devmate.common.api.ApiResponse;
import com.devmate.knowledge.dto.BusinessFeatureDetailResponse;
import com.devmate.knowledge.dto.ProjectBusinessMapResponse;
import com.devmate.knowledge.service.ProjectBusinessMapService;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/business-map")
@Validated
public class ProjectBusinessMapController {

    private final ProjectBusinessMapService businessMapService;

    public ProjectBusinessMapController(ProjectBusinessMapService businessMapService) {
        this.businessMapService = businessMapService;
    }

    @GetMapping
    public ApiResponse<ProjectBusinessMapResponse> getBusinessMap(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId
    ) {
        return ApiResponse.success(businessMapService.getBusinessMap(projectId));
    }

    @GetMapping("/features/{featureId}")
    public ApiResponse<BusinessFeatureDetailResponse> getFeatureDetail(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId,
            @Positive(message = "功能ID必须大于0") @PathVariable Long featureId
    ) {
        return ApiResponse.success(businessMapService.getFeatureDetail(projectId, featureId));
    }
}
