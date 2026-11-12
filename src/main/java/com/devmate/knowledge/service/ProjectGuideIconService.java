package com.devmate.knowledge.service;

import com.devmate.knowledge.mapper.ProjectUnderstandingReportMapper;
import com.devmate.user.config.SecurityProperties;
import com.devmate.user.service.CurrentUserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Bounded list projection; never hydrate code or invoke a model for project cards. */
@Service
public class ProjectGuideIconService {
    private final ProjectUnderstandingReportMapper reportMapper;
    private final CurrentUserService currentUserService;
    private final SecurityProperties securityProperties;
    private final ObjectMapper objectMapper;

    public ProjectGuideIconService(ProjectUnderstandingReportMapper reportMapper, CurrentUserService currentUserService,
                                  SecurityProperties securityProperties, ObjectMapper objectMapper) {
        this.reportMapper = reportMapper;
        this.currentUserService = currentUserService;
        this.securityProperties = securityProperties;
        this.objectMapper = objectMapper;
    }

    public Map<Long, String> forProjects(List<Long> projectIds) {
        if (projectIds.isEmpty() || !securityProperties.isEnabled()) return Map.of();
        if (projectIds.size() > 100) throw new IllegalArgumentException("图标查询超出分页限制");
        var result = new HashMap<Long, String>();
        for (var report : reportMapper.latestVisibleIconReports(currentUserService.getRequiredUser().id(), projectIds)) {
            if (report.getReportJson() == null) continue;
            try {
                String icon = ProjectGuideAppearance.safeIcon(objectMapper.readTree(report.getReportJson())
                        .path("guide").path("content").path("appearance").path("projectIcon").asText(null));
                if (icon != null) result.put(report.getProjectId(), icon);
            } catch (JsonProcessingException ignored) {
                // A damaged/legacy presentation must not hide the user's projects or leak report JSON.
            }
        }
        return Map.copyOf(result);
    }
}
