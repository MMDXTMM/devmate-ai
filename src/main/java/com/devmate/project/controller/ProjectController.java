package com.devmate.project.controller;

import com.devmate.common.api.ApiResponse;
import com.devmate.common.api.PageResponse;
import com.devmate.project.dto.CreateProjectRequest;
import com.devmate.project.dto.ProjectQueryRequest;
import com.devmate.project.dto.ProjectResponse;
import com.devmate.project.dto.UpdateProjectRequest;
import com.devmate.project.service.ProjectService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
@Validated
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProjectResponse>> createProject(
            @Valid @RequestBody CreateProjectRequest request
    ) {
        ProjectResponse project = projectService.createProject(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(project));
    }

    @GetMapping("/{projectId}")
    public ApiResponse<ProjectResponse> getProject(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId
    ) {
        return ApiResponse.success(projectService.getProject(projectId));
    }

    @GetMapping
    public ApiResponse<PageResponse<ProjectResponse>> listProjects(
            @Valid @ModelAttribute ProjectQueryRequest request
    ) {
        return ApiResponse.success(projectService.listProjects(request));
    }

    @PutMapping("/{projectId}")
    public ApiResponse<ProjectResponse> updateProject(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId,
            @Valid @RequestBody UpdateProjectRequest request
    ) {
        return ApiResponse.success(projectService.updateProject(projectId, request));
    }

    @DeleteMapping("/{projectId}")
    public ApiResponse<Void> deleteProject(
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId
    ) {
        projectService.deleteProject(projectId);
        return ApiResponse.success(null);
    }
}
