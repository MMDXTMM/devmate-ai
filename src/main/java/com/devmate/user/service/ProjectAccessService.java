package com.devmate.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devmate.common.error.BusinessException;
import com.devmate.common.error.ErrorCode;
import com.devmate.user.entity.ProjectMember;
import com.devmate.user.mapper.ProjectMemberMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectAccessService {

    private static final String OWNER = "OWNER";

    private final ProjectMemberMapper projectMemberMapper;
    private final CurrentUserService currentUserService;

    public ProjectAccessService(
            ProjectMemberMapper projectMemberMapper,
            CurrentUserService currentUserService
    ) {
        this.projectMemberMapper = projectMemberMapper;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public void requireMember(Long projectId) {
        requireRole(projectId, null);
    }

    @Transactional(readOnly = true)
    public void requireOwner(Long projectId) {
        requireRole(projectId, OWNER);
    }

    private void requireRole(Long projectId, String role) {
        Long userId = currentUserService.getRequiredUser().id();
        var query = Wrappers.lambdaQuery(ProjectMember.class)
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getUserId, userId);
        if (role != null) {
            query.eq(ProjectMember::getMemberRole, role);
        }
        if (projectMemberMapper.selectCount(query) != 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
