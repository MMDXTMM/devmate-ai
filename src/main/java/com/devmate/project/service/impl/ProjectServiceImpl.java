package com.devmate.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devmate.common.api.PageResponse;
import com.devmate.common.error.BusinessException;
import com.devmate.common.error.ErrorCode;
import com.devmate.project.dto.CreateProjectRequest;
import com.devmate.project.dto.ProjectQueryRequest;
import com.devmate.project.dto.ProjectResponse;
import com.devmate.project.dto.UpdateProjectRequest;
import com.devmate.project.entity.Project;
import com.devmate.project.mapper.ProjectMapper;
import com.devmate.project.model.ProjectSourceType;
import com.devmate.project.model.ProjectStatus;
import com.devmate.project.service.ProjectService;
import com.devmate.user.config.SecurityProperties;
import com.devmate.user.entity.ProjectMember;
import com.devmate.user.mapper.ProjectMemberMapper;
import com.devmate.user.service.CurrentUserService;
import com.devmate.user.service.ProjectAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProjectServiceImpl implements ProjectService {

    private static final String DEFAULT_SOURCE_TYPE = ProjectSourceType.LOCAL.name();
    private static final String INITIAL_STATUS = ProjectStatus.CREATED.name();
    private static final String OWNER_ROLE = "OWNER";

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final CurrentUserService currentUserService;
    private final ProjectAccessService projectAccessService;
    private final SecurityProperties securityProperties;

    public ProjectServiceImpl(
            ProjectMapper projectMapper,
            ProjectMemberMapper projectMemberMapper,
            CurrentUserService currentUserService,
            ProjectAccessService projectAccessService,
            SecurityProperties securityProperties
    ) {
        this.projectMapper = projectMapper;
        this.projectMemberMapper = projectMemberMapper;
        this.currentUserService = currentUserService;
        this.projectAccessService = projectAccessService;
        this.securityProperties = securityProperties;
    }

    @Override
    @Transactional
    public ProjectResponse createProject(CreateProjectRequest request) {
        String sourceType = StringUtils.hasText(request.sourceType())
                ? request.sourceType()
                : DEFAULT_SOURCE_TYPE;

        validateSourceLocation(sourceType, request.sourceLocation());

        LocalDateTime now = LocalDateTime.now();
        Project project = new Project();
        Long ownerId = securityProperties.isEnabled() ? currentUserService.getRequiredUser().id() : null;
        project.setOwnerId(ownerId);
        project.setName(request.name().trim());
        project.setDescription(trimToNull(request.description()));
        project.setSourceType(sourceType);
        project.setSourceLocation(trimToNull(request.sourceLocation()));
        project.setDefaultBranch(trimToNull(request.defaultBranch()));
        project.setStatus(INITIAL_STATUS);
        project.setDeleted(0);
        project.setCreatedAt(now);
        project.setUpdatedAt(now);

        int insertedRows = projectMapper.insert(project);
        if (insertedRows != 1) {
            throw new IllegalStateException("项目创建失败");
        }
        if (ownerId != null) {
            ProjectMember owner = new ProjectMember();
            owner.setProjectId(project.getId());
            owner.setUserId(ownerId);
            owner.setMemberRole(OWNER_ROLE);
            owner.setCreatedAt(now);
            if (projectMemberMapper.insert(owner) != 1) {
                throw new IllegalStateException("项目所有者关系创建失败");
            }
        }

        return ProjectResponse.from(project);
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectResponse getProject(Long projectId) {
        requireMemberWhenEnabled(projectId);
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "项目不存在");
        }
        return ProjectResponse.from(project);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProjectResponse> listProjects(ProjectQueryRequest request) {
        LambdaQueryWrapper<Project> query = Wrappers.lambdaQuery(Project.class)
                .like(StringUtils.hasText(request.name()), Project::getName, trimToNull(request.name()))
                .eq(StringUtils.hasText(request.status()), Project::getStatus, request.status())
                .orderByDesc(Project::getCreatedAt);
        if (securityProperties.isEnabled()) {
            Long userId = currentUserService.getRequiredUser().id();
            query.inSql(Project::getId, "SELECT project_id FROM project_member WHERE user_id = " + userId);
        }

        Page<Project> result = projectMapper.selectPage(
                Page.of(request.page(), request.size()),
                query
        );
        List<ProjectResponse> items = result.getRecords()
                .stream()
                .map(ProjectResponse::from)
                .toList();

        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getPages(),
                items
        );
    }

    @Override
    @Transactional
    public ProjectResponse updateProject(Long projectId, UpdateProjectRequest request) {
        requireOwnerWhenEnabled(projectId);
        Project project = findProject(projectId);
        validateSourceLocation(request.sourceType(), request.sourceLocation());

        project.setName(request.name().trim());
        project.setDescription(trimToNull(request.description()));
        project.setSourceType(request.sourceType());
        project.setSourceLocation(trimToNull(request.sourceLocation()));
        project.setDefaultBranch(trimToNull(request.defaultBranch()));
        project.setUpdatedAt(LocalDateTime.now());

        LambdaUpdateWrapper<Project> update = Wrappers.lambdaUpdate(Project.class)
                .eq(Project::getId, projectId)
                .set(Project::getName, project.getName())
                .set(Project::getDescription, project.getDescription())
                .set(Project::getSourceType, project.getSourceType())
                .set(Project::getSourceLocation, project.getSourceLocation())
                .set(Project::getDefaultBranch, project.getDefaultBranch())
                .set(Project::getUpdatedAt, project.getUpdatedAt());
        int updatedRows = projectMapper.update(update);
        if (updatedRows != 1) {
            throw new IllegalStateException("项目修改失败");
        }
        return ProjectResponse.from(project);
    }

    @Override
    @Transactional
    public void deleteProject(Long projectId) {
        requireOwnerWhenEnabled(projectId);
        int deletedRows = projectMapper.deleteById(projectId);
        if (deletedRows != 1) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "项目不存在");
        }
    }

    private Project findProject(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "项目不存在");
        }
        return project;
    }

    private void validateSourceLocation(String sourceType, String sourceLocation) {
        if (ProjectSourceType.GIT.name().equals(sourceType)
                && !StringUtils.hasText(sourceLocation)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Git项目必须填写仓库地址");
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void requireMemberWhenEnabled(Long projectId) {
        if (securityProperties.isEnabled()) {
            projectAccessService.requireMember(projectId);
        }
    }

    private void requireOwnerWhenEnabled(Long projectId) {
        if (securityProperties.isEnabled()) {
            projectAccessService.requireOwner(projectId);
        }
    }
}
