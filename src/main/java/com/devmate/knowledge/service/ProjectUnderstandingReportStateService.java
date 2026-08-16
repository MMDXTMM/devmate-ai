package com.devmate.knowledge.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devmate.common.error.BusinessException;
import com.devmate.common.error.ErrorCode;
import com.devmate.agent.config.ProjectUnderstandingProperties;
import com.devmate.knowledge.entity.ProjectUnderstandingReport;
import com.devmate.knowledge.mapper.ProjectUnderstandingReportMapper;
import com.devmate.project.entity.Project;
import com.devmate.project.mapper.ProjectMapper;
import com.devmate.user.service.CurrentUserService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class ProjectUnderstandingReportStateService {
    private final ProjectUnderstandingReportMapper reportMapper;
    private final ProjectMapper projectMapper;
    private final CurrentUserService currentUserService;
    private final ProjectUnderstandingProperties properties;

    public ProjectUnderstandingReportStateService(
            ProjectUnderstandingReportMapper reportMapper,
            ProjectMapper projectMapper,
            CurrentUserService currentUserService,
            ProjectUnderstandingProperties properties
    ) {
        this.reportMapper = reportMapper;
        this.projectMapper = projectMapper;
        this.currentUserService = currentUserService;
        this.properties = properties;
    }

    @Transactional
    public PreparedReport prepare(Long projectId, String revision, String attemptKey,
                                  String provider, String modelName, String promptVersion) {
        Long userId = currentUserService.getRequiredUser().id();
        ProjectUnderstandingReport previous = byAttempt(userId, attemptKey);
        if (previous != null) return reused(previous, projectId, revision);
        Project project = projectMapper.selectById(projectId);
        if (project == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "项目不存在");
        if (!"READY".equals(project.getStatus()) || !Objects.equals(revision, project.getCurrentRevision())) {
            throw new BusinessException(ErrorCode.CONFLICT, "项目源码版本已变化，请刷新后重新生成报告");
        }
        recoverStale(projectId, revision);
        LocalDateTime now = LocalDateTime.now();
        ProjectUnderstandingReport report = new ProjectUnderstandingReport();
        report.setProjectId(projectId);
        report.setUserId(userId);
        report.setRevision(revision);
        report.setProvider(provider);
        report.setModelName(modelName);
        report.setPromptVersion(promptVersion);
        report.setStatus("RUNNING");
        report.setPromptTokens(0);
        report.setCompletionTokens(0);
        report.setTotalTokens(0);
        report.setAttemptKey(attemptKey);
        report.setRunningKey(projectId + ":" + revision);
        report.setCreatedAt(now);
        report.setStartedAt(now);
        try {
            reportMapper.insert(report);
            return new PreparedReport(report, false);
        } catch (DataIntegrityViolationException exception) {
            previous = byAttempt(userId, attemptKey);
            if (previous != null) return reused(previous, projectId, revision);
            throw new BusinessException(ErrorCode.CONFLICT, "当前项目已有深度理解报告正在生成");
        }
    }

    @Transactional
    public ProjectUnderstandingReport complete(Long reportId, String reportJson,
                                               int promptTokens, int completionTokens,
                                               int totalTokens, long latencyMs) {
        int updated = reportMapper.update(null, Wrappers.lambdaUpdate(ProjectUnderstandingReport.class)
                .eq(ProjectUnderstandingReport::getId, reportId)
                .eq(ProjectUnderstandingReport::getStatus, "RUNNING")
                .set(ProjectUnderstandingReport::getStatus, "SUCCEEDED")
                .set(ProjectUnderstandingReport::getReportJson, reportJson)
                .set(ProjectUnderstandingReport::getPromptTokens, promptTokens)
                .set(ProjectUnderstandingReport::getCompletionTokens, completionTokens)
                .set(ProjectUnderstandingReport::getTotalTokens, totalTokens)
                .set(ProjectUnderstandingReport::getLatencyMs, latencyMs)
                .set(ProjectUnderstandingReport::getRunningKey, null)
                .set(ProjectUnderstandingReport::getFinishedAt, LocalDateTime.now()));
        if (updated != 1) throw new BusinessException(ErrorCode.CONFLICT, "报告状态已变化，请刷新后查看");
        return reportMapper.selectById(reportId);
    }

    @Transactional
    public void fail(Long reportId, String errorCode, String errorMessage, long latencyMs) {
        reportMapper.update(null, Wrappers.lambdaUpdate(ProjectUnderstandingReport.class)
                .eq(ProjectUnderstandingReport::getId, reportId)
                .eq(ProjectUnderstandingReport::getStatus, "RUNNING")
                .set(ProjectUnderstandingReport::getStatus, "FAILED")
                .set(ProjectUnderstandingReport::getErrorCode, limited(errorCode, 100))
                .set(ProjectUnderstandingReport::getErrorMessage, limited(errorMessage, 500))
                .set(ProjectUnderstandingReport::getLatencyMs, latencyMs)
                .set(ProjectUnderstandingReport::getRunningKey, null)
                .set(ProjectUnderstandingReport::getFinishedAt, LocalDateTime.now()));
    }

    @Transactional(readOnly = true)
    public ProjectUnderstandingReport latest(Long projectId) {
        return reportMapper.selectOne(Wrappers.lambdaQuery(ProjectUnderstandingReport.class)
                .eq(ProjectUnderstandingReport::getProjectId, projectId)
                .orderByDesc(ProjectUnderstandingReport::getCreatedAt)
                .orderByDesc(ProjectUnderstandingReport::getId)
                .last("LIMIT 1"));
    }

    private ProjectUnderstandingReport byAttempt(Long userId, String attemptKey) {
        return reportMapper.selectOne(Wrappers.lambdaQuery(ProjectUnderstandingReport.class)
                .eq(ProjectUnderstandingReport::getUserId, userId)
                .eq(ProjectUnderstandingReport::getAttemptKey, attemptKey));
    }

    private void recoverStale(Long projectId, String revision) {
        LocalDateTime cutoff = LocalDateTime.now().minus(properties.getStaleTaskTimeout());
        reportMapper.update(null, Wrappers.lambdaUpdate(ProjectUnderstandingReport.class)
                .eq(ProjectUnderstandingReport::getProjectId, projectId)
                .eq(ProjectUnderstandingReport::getRevision, revision)
                .eq(ProjectUnderstandingReport::getStatus, "RUNNING")
                .le(ProjectUnderstandingReport::getStartedAt, cutoff)
                .set(ProjectUnderstandingReport::getStatus, "FAILED")
                .set(ProjectUnderstandingReport::getErrorCode, "STALE_TASK")
                .set(ProjectUnderstandingReport::getErrorMessage, "报告生成任务超时，可重新发起")
                .set(ProjectUnderstandingReport::getRunningKey, null)
                .set(ProjectUnderstandingReport::getFinishedAt, LocalDateTime.now()));
    }

    private PreparedReport reused(ProjectUnderstandingReport report, Long projectId, String revision) {
        if (!Objects.equals(report.getProjectId(), projectId) || !Objects.equals(report.getRevision(), revision)) {
            throw new BusinessException(ErrorCode.CONFLICT, "请求标识已用于其他项目或版本");
        }
        return new PreparedReport(report, true);
    }

    private String limited(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    public record PreparedReport(ProjectUnderstandingReport report, boolean reused) { }
}
