package com.devmate.project.service;

import com.devmate.common.api.PageResponse;
import com.devmate.project.dto.CreateProjectRequest;
import com.devmate.project.dto.ProjectQueryRequest;
import com.devmate.project.dto.ProjectResponse;
import com.devmate.project.dto.UpdateProjectRequest;

public interface ProjectService {

    ProjectResponse createProject(CreateProjectRequest request);

    ProjectResponse getProject(Long projectId);

    PageResponse<ProjectResponse> listProjects(ProjectQueryRequest request);

    ProjectResponse updateProject(Long projectId, UpdateProjectRequest request);

    void deleteProject(Long projectId);
}
