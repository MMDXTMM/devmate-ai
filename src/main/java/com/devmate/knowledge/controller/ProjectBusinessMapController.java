package com.devmate.knowledge.controller;

import com.devmate.common.api.ApiResponse;
import com.devmate.knowledge.dto.BusinessFeatureDetailResponse;
import com.devmate.knowledge.dto.ProjectBusinessMapResponse;
import com.devmate.knowledge.service.ProjectBusinessMapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/business-map")
@Validated
@Tag(name = "项目业务地图", description = "将 Controller、Service、数据访问和代码证据组织为可阅读的业务功能")
public class ProjectBusinessMapController {

    private final ProjectBusinessMapService businessMapService;

    public ProjectBusinessMapController(ProjectBusinessMapService businessMapService) {
        this.businessMapService = businessMapService;
    }

    @GetMapping
    @Operation(summary = "生成项目业务地图")
    public ApiResponse<ProjectBusinessMapResponse> getBusinessMap(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId
    ) {
        return ApiResponse.success(businessMapService.getBusinessMap(projectId));
    }

    @GetMapping("/features/{featureId}")
    @Operation(summary = "查询业务功能实现链路", description = "展示接口入口、实现步骤、数据访问和真实代码证据。")
    public ApiResponse<BusinessFeatureDetailResponse> getFeatureDetail(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId,
            @Positive(message = "功能ID必须大于0") @PathVariable Long featureId
    ) {
        return ApiResponse.success(businessMapService.getFeatureDetail(projectId, featureId));
    }
}
