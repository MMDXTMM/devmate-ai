package com.devmate.review.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devmate.common.error.BusinessException;
import com.devmate.common.error.ErrorCode;
import com.devmate.review.config.ReviewWorkflowProperties;
import com.devmate.review.dto.AiReviewResponse;
import com.devmate.review.dto.ReviewWorkflowResponse;
import com.devmate.review.dto.ReviewDiffResponse;
import com.devmate.review.dto.StaticAnalysisResponse;
import com.devmate.review.entity.ReviewWorkflowRun;
import com.devmate.review.mapper.ReviewWorkflowRunMapper;
import com.devmate.review.model.ReviewWorkflowStage;
import com.devmate.knowledge.dto.EmbeddingIndexTaskResponse;
import com.devmate.knowledge.dto.IndexTaskResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ReviewWorkflowStateService {

    private final ReviewWorkflowRunMapper mapper;
    private final ReviewWorkflowProperties properties;

    public ReviewWorkflowStateService(
            ReviewWorkflowRunMapper mapper,
            ReviewWorkflowProperties properties
    ) {
        this.mapper = mapper;
        this.properties = properties;
    }

    @Transactional
    public ReviewWorkflowStart prepare(Long projectId, String attemptKey) {
        ReviewWorkflowRun existingAttempt = findByAttempt(attemptKey);
        if (existingAttempt != null) {
            requireSameProject(projectId, existingAttempt);
            return new ReviewWorkflowStart(existingAttempt, false);
        }

        String runningKey = projectId.toString();
        ReviewWorkflowRun running = mapper.selectOne(Wrappers.lambdaQuery(ReviewWorkflowRun.class)
                .eq(ReviewWorkflowRun::getRunningKey, runningKey)
                .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        if (running != null) {
            if (!isStale(running, now)) {
                throw new BusinessException(ErrorCode.CONFLICT, "当前项目已有代码审查正在运行");
            }
            expire(running, now);
        }

        ReviewWorkflowRun run = new ReviewWorkflowRun();
        run.setProjectId(projectId);
        run.setAttemptKey(attemptKey);
        run.setStatus("RUNNING");
        run.setCurrentStage(ReviewWorkflowStage.SOURCE_IMPORT.name());
        run.setRunningKey(runningKey);
        run.setCreatedAt(now);
        run.setStartedAt(now);
        try {
            mapper.insert(run);
            return new ReviewWorkflowStart(run, true);
        } catch (DataIntegrityViolationException exception) {
            ReviewWorkflowRun racedAttempt = findByAttempt(attemptKey);
            if (racedAttempt != null) {
                requireSameProject(projectId, racedAttempt);
                return new ReviewWorkflowStart(racedAttempt, false);
            }
            throw new BusinessException(ErrorCode.CONFLICT, "当前项目已有代码审查正在运行");
        }
    }

    @Transactional
    public ReviewWorkflowRun sourceImported(Long runId, Long taskId) {
        ReviewWorkflowRun run = requireRunning(runId);
        run.setIndexTaskId(taskId);
        run.setCurrentStage(ReviewWorkflowStage.DIFF.name());
        mapper.updateById(run);
        return run;
    }

    @Transactional
    public ReviewWorkflowRun diffCompleted(Long runId, Long taskId) {
        ReviewWorkflowRun run = requireRunning(runId);
        run.setReviewTaskId(taskId);
        run.setCurrentStage(ReviewWorkflowStage.STATIC_ANALYSIS.name());
        mapper.updateById(run);
        return run;
    }

    @Transactional
    public ReviewWorkflowRun staticAnalysisCompleted(Long runId, Long taskId) {
        ReviewWorkflowRun run = requireRunning(runId);
        run.setStaticAnalysisTaskId(taskId);
        run.setCurrentStage(ReviewWorkflowStage.EMBEDDING.name());
        mapper.updateById(run);
        return run;
    }

    @Transactional
    public ReviewWorkflowRun embeddingCompleted(Long runId, Long taskId) {
        ReviewWorkflowRun run = requireRunning(runId);
        run.setEmbeddingTaskId(taskId);
        run.setCurrentStage(ReviewWorkflowStage.AGENT_REVIEW.name());
        mapper.updateById(run);
        return run;
    }

    @Transactional
    public ReviewWorkflowRun complete(Long runId, Long aiReviewTaskId) {
        ReviewWorkflowRun run = requireRunning(runId);
        run.setAiReviewTaskId(aiReviewTaskId);
        run.setStatus("SUCCEEDED");
        run.setCurrentStage(ReviewWorkflowStage.COMPLETED.name());
        run.setRunningKey(null);
        run.setFinishedAt(LocalDateTime.now());
        mapper.update(null, Wrappers.lambdaUpdate(ReviewWorkflowRun.class)
                .eq(ReviewWorkflowRun::getId, runId)
                .set(ReviewWorkflowRun::getAiReviewTaskId, aiReviewTaskId)
                .set(ReviewWorkflowRun::getStatus, "SUCCEEDED")
                .set(ReviewWorkflowRun::getCurrentStage, ReviewWorkflowStage.COMPLETED.name())
                .set(ReviewWorkflowRun::getRunningKey, null)
                .set(ReviewWorkflowRun::getFinishedAt, run.getFinishedAt()));
        return run;
    }

    @Transactional
    public ReviewWorkflowRun fail(
            Long runId,
            ReviewWorkflowStage stage,
            String message,
            String recoveryAction
    ) {
        ReviewWorkflowRun run = mapper.selectById(runId);
        if (run == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "代码审查运行不存在");
        }
        run.setStatus("FAILED");
        run.setCurrentStage(stage.name());
        run.setRunningKey(null);
        run.setErrorMessage(message);
        run.setRecoveryAction(recoveryAction);
        run.setFinishedAt(LocalDateTime.now());
        mapper.update(null, Wrappers.lambdaUpdate(ReviewWorkflowRun.class)
                .eq(ReviewWorkflowRun::getId, runId)
                .set(ReviewWorkflowRun::getStatus, "FAILED")
                .set(ReviewWorkflowRun::getCurrentStage, stage.name())
                .set(ReviewWorkflowRun::getRunningKey, null)
                .set(ReviewWorkflowRun::getErrorMessage, message)
                .set(ReviewWorkflowRun::getRecoveryAction, recoveryAction)
                .set(ReviewWorkflowRun::getFinishedAt, run.getFinishedAt()));
        return run;
    }

    @Transactional(readOnly = true)
    public ReviewWorkflowRun latest(Long projectId) {
        ReviewWorkflowRun run = mapper.selectOne(Wrappers.lambdaQuery(ReviewWorkflowRun.class)
                .eq(ReviewWorkflowRun::getProjectId, projectId)
                .orderByDesc(ReviewWorkflowRun::getCreatedAt)
                .orderByDesc(ReviewWorkflowRun::getId)
                .last("LIMIT 1"));
        if (run == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "当前项目还没有代码审查运行");
        }
        return run;
    }

    public ReviewWorkflowResponse response(ReviewWorkflowRun run) {
        return response(run, null, null, null, null, null);
    }

    public ReviewWorkflowResponse response(
            ReviewWorkflowRun run,
            IndexTaskResponse sourceImport,
            ReviewDiffResponse reviewDiff,
            StaticAnalysisResponse staticAnalysis,
            EmbeddingIndexTaskResponse embeddingIndex,
            AiReviewResponse aiReview
    ) {
        return new ReviewWorkflowResponse(
                run.getId(), run.getProjectId(), run.getAttemptKey(), run.getStatus(),
                run.getCurrentStage(), run.getIndexTaskId(), run.getReviewTaskId(),
                run.getStaticAnalysisTaskId(), run.getEmbeddingTaskId(), run.getAiReviewTaskId(),
                run.getErrorMessage(), run.getRecoveryAction(), run.getCreatedAt(),
                run.getStartedAt(), run.getFinishedAt(), sourceImport, reviewDiff,
                staticAnalysis, embeddingIndex, aiReview
        );
    }

    private ReviewWorkflowRun requireRunning(Long runId) {
        ReviewWorkflowRun run = mapper.selectById(runId);
        if (run == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "代码审查运行不存在");
        }
        if (!"RUNNING".equals(run.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "代码审查运行状态已变化");
        }
        return run;
    }

    private ReviewWorkflowRun findByAttempt(String attemptKey) {
        return mapper.selectOne(Wrappers.lambdaQuery(ReviewWorkflowRun.class)
                .eq(ReviewWorkflowRun::getAttemptKey, attemptKey)
                .last("LIMIT 1"));
    }

    private void requireSameProject(Long projectId, ReviewWorkflowRun run) {
        if (!projectId.equals(run.getProjectId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "请求标识已被其他项目使用");
        }
    }

    private boolean isStale(ReviewWorkflowRun run, LocalDateTime now) {
        return run.getStartedAt() == null
                || run.getStartedAt().plus(properties.getStaleTimeout()).isBefore(now);
    }

    private void expire(ReviewWorkflowRun run, LocalDateTime now) {
        run.setStatus("FAILED");
        run.setRunningKey(null);
        run.setErrorMessage("代码审查运行超时，已允许重新执行");
        run.setRecoveryAction("请重新点击开始代码审查");
        run.setFinishedAt(now);
        mapper.update(null, Wrappers.lambdaUpdate(ReviewWorkflowRun.class)
                .eq(ReviewWorkflowRun::getId, run.getId())
                .set(ReviewWorkflowRun::getStatus, "FAILED")
                .set(ReviewWorkflowRun::getRunningKey, null)
                .set(ReviewWorkflowRun::getErrorMessage, run.getErrorMessage())
                .set(ReviewWorkflowRun::getRecoveryAction, run.getRecoveryAction())
                .set(ReviewWorkflowRun::getFinishedAt, now));
    }
}
