package com.devmate.knowledge.service;

import com.devmate.knowledge.entity.ProjectUnderstandingReport;
import com.devmate.knowledge.mapper.ProjectUnderstandingReportMapper;
import com.devmate.project.dto.CreateProjectRequest;
import com.devmate.project.dto.ProjectQueryRequest;
import com.devmate.project.mapper.ProjectMapper;
import com.devmate.project.service.ProjectService;
import com.devmate.user.entity.AppUser;
import com.devmate.user.entity.ProjectMember;
import com.devmate.user.mapper.AppUserMapper;
import com.devmate.user.mapper.ProjectMemberMapper;
import com.devmate.user.security.AuthenticatedUser;
import com.devmate.user.service.CurrentUserService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {"devmate.security.enabled=true", "devmate.security.jwt-secret=test-jwt-secret-with-at-least-32-characters"})
@ActiveProfiles("test")
@Transactional
class ProjectGuideIconServiceTest {
    @Autowired private ProjectGuideIconService icons;
    @Autowired private ProjectService projects;
    @Autowired private ProjectMapper projectMapper;
    @Autowired private AppUserMapper users;
    @Autowired private ProjectMemberMapper members;
    @Autowired private ProjectUnderstandingReportMapper reports;
    @MockitoBean private CurrentUserService currentUser;
    private Long projectId;
    private final LocalDateTime now = LocalDateTime.now();

    @BeforeEach void prepare() {
        for (long id : List.of(9301L, 9302L)) {
            var user = new AppUser(); user.setId(id); user.setUsername("icon-" + id);
            user.setPasswordHash("not-used"); user.setStatus("ACTIVE"); user.setDeleted(0); users.insert(user);
        }
        when(currentUser.getRequiredUser()).thenReturn(new AuthenticatedUser(9301L, "icon-9301"));
        projectId = projects.createProject(new CreateProjectRequest("new-domain", "unknown purpose", "LOCAL", null, null)).id();
        var project = projectMapper.selectById(projectId); project.setCurrentRevision("current"); project.setStatus("READY");
        projectMapper.updateById(project);
    }

    @Test void returnsOnlyCurrentUsersLatestSuccessfulCurrentRevisionIconThroughListAndDetail() {
        save(9301L, "current", "SUCCEEDED", "🔭", now.minusSeconds(1));
        save(9301L, "current", "SUCCEEDED", "🦋", now);
        save(9301L, "stale", "SUCCEEDED", "🌈", now.plusSeconds(1));
        save(9302L, "current", "SUCCEEDED", "🐙", now.plusSeconds(2));
        save(9301L, "current", "FAILED", "💣", now.plusSeconds(3));
        assertThat(icons.forProjects(List.of(projectId))).containsEntry(projectId, "🦋");
        assertThat(projects.getProject(projectId).icon()).isEqualTo("🦋");
        assertThat(projects.listProjects(new ProjectQueryRequest(1, 20, null, null)).items()).singleElement()
                .satisfies(item -> assertThat(item.icon()).isEqualTo("🦋"));
        var shared = new ProjectMember(); shared.setProjectId(projectId); shared.setUserId(9302L);
        shared.setMemberRole("MEMBER"); shared.setCreatedAt(now); members.insert(shared);
        when(currentUser.getRequiredUser()).thenReturn(new AuthenticatedUser(9302L, "icon-9302"));
        assertThat(icons.forProjects(List.of(projectId))).containsEntry(projectId, "🐙");
    }

    @Test void handlesLegacyInvalidAndCorruptPresentationWithoutLeakingOrHidingProjects() {
        assertThat(icons.forProjects(List.of())).isEmpty();
        assertThat(projects.getProject(projectId).icon()).isNull();
        var report = save(9301L, "current", "SUCCEEDED", "🔭", now);
        for (String value : List.of("{}", "{broken", "{\"guide\":{\"content\":{\"appearance\":{\"projectIcon\":\"https://example.com/icon.svg\"}}}}")) {
            report.setReportJson(value); reports.updateById(report);
            assertThat(projects.getProject(projectId).icon()).isNull();
            assertThat(projects.listProjects(new ProjectQueryRequest(1, 20, null, null)).items()).hasSize(1);
        }
    }

    @Test void ignoresReportsForNonMembersAndDeletedProjectsAndStaleSnapshots() {
        save(9301L, "stale", "SUCCEEDED", "🔭", now);
        assertThat(icons.forProjects(List.of(projectId))).isEmpty();
        save(9301L, "current", "SUCCEEDED", "🔭", now);
        when(currentUser.getRequiredUser()).thenReturn(new AuthenticatedUser(9302L, "icon-9302"));
        assertThat(icons.forProjects(List.of(projectId))).isEmpty();
        when(currentUser.getRequiredUser()).thenReturn(new AuthenticatedUser(9301L, "icon-9301"));
        projectMapper.deleteById(projectId);
        assertThat(icons.forProjects(List.of(projectId))).isEmpty();
    }

    private ProjectUnderstandingReport save(long userId, String revision, String status, String icon, LocalDateTime at) {
        var report = new ProjectUnderstandingReport(); report.setProjectId(projectId); report.setUserId(userId);
        report.setRevision(revision); report.setProvider("MOCK"); report.setModelName("test"); report.setPromptVersion("project-guide-v4");
        report.setStatus(status); report.setPromptTokens(0); report.setCompletionTokens(0); report.setTotalTokens(0);
        report.setCreatedAt(at); report.setAttemptKey(UUID.randomUUID().toString());
        report.setReportJson("{\"guide\":{\"content\":{\"appearance\":{\"projectIcon\":\"" + icon + "\"}}}}");
        reports.insert(report); return report;
    }
}
