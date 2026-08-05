package com.devmate.review.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devmate.agent.entity.AiInvocationLog;
import com.devmate.agent.config.AiReviewProperties;
import com.devmate.agent.mapper.AiInvocationLogMapper;
import com.devmate.agent.model.AiReviewModelResult;
import com.devmate.common.error.BusinessException;
import com.devmate.common.error.ErrorCode;
import com.devmate.knowledge.dto.RetrievalSearchResponse;
import com.devmate.project.mapper.ProjectMapper;
import com.devmate.review.dto.AiReviewFindingResponse;
import com.devmate.review.dto.AiReviewResponse;
import com.devmate.review.dto.ReviewFeedbackResponse;
import com.devmate.review.dto.ToolCallResponse;
import com.devmate.review.entity.AiReviewTask;
import com.devmate.review.entity.CodeReviewFeedback;
import com.devmate.review.entity.CodeReviewTask;
import com.devmate.review.entity.ReviewFinding;
import com.devmate.review.entity.StaticAnalysisTask;
import com.devmate.review.mapper.AiReviewTaskMapper;
import com.devmate.review.mapper.CodeReviewFeedbackMapper;
import com.devmate.review.mapper.CodeReviewTaskMapper;
import com.devmate.review.mapper.ReviewFindingMapper;
import com.devmate.review.mapper.StaticAnalysisTaskMapper;
import com.devmate.review.model.AiFindingValidationResult;
import com.devmate.review.model.ReviewExecutionMode;
import com.devmate.review.model.ValidatedAiFinding;
import com.devmate.tool.entity.ToolCallLog;
import com.devmate.tool.mapper.ToolCallLogMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AiReviewStateService {

    private final ProjectMapper projectMapper;
    private final CodeReviewTaskMapper reviewTaskMapper;
    private final StaticAnalysisTaskMapper staticTaskMapper;
    private final ReviewFindingMapper findingMapper;
    private final AiInvocationLogMapper invocationMapper;
    private final AiReviewTaskMapper aiReviewTaskMapper;
    private final CodeReviewFeedbackMapper feedbackMapper;
    private final ToolCallLogMapper toolCallLogMapper;
    private final AiReviewProperties properties;

    public AiReviewStateService(
            ProjectMapper projectMapper,
            CodeReviewTaskMapper reviewTaskMapper,
            StaticAnalysisTaskMapper staticTaskMapper,
            ReviewFindingMapper findingMapper,
            AiInvocationLogMapper invocationMapper,
            AiReviewTaskMapper aiReviewTaskMapper,
            CodeReviewFeedbackMapper feedbackMapper,
            ToolCallLogMapper toolCallLogMapper,
            AiReviewProperties properties
    ) {
        this.projectMapper = projectMapper;
        this.reviewTaskMapper = reviewTaskMapper;
        this.staticTaskMapper = staticTaskMapper;
        this.findingMapper = findingMapper;
        this.invocationMapper = invocationMapper;
        this.aiReviewTaskMapper = aiReviewTaskMapper;
        this.feedbackMapper = feedbackMapper;
        this.toolCallLogMapper = toolCallLogMapper;
        this.properties = properties;
    }

    @Transactional
    public AiReviewContext prepare(
            Long projectId,
            String provider,
            String modelName,
            String promptVersion,
            ReviewExecutionMode executionMode
    ) {
        if (projectMapper.selectById(projectId) == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "项目不存在");
        }
        CodeReviewTask reviewTask = reviewTaskMapper.selectOne(
                Wrappers.lambdaQuery(CodeReviewTask.class)
                        .eq(CodeReviewTask::getProjectId, projectId)
                        .eq(CodeReviewTask::getStatus, "SUCCEEDED")
                        .orderByDesc(CodeReviewTask::getCreatedAt)
                        .last("LIMIT 1")
        );
        if (reviewTask == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "请先生成成功的Diff覆盖报告");
        }
        StaticAnalysisTask staticTask = staticTaskMapper.selectOne(
                Wrappers.lambdaQuery(StaticAnalysisTask.class)
                        .eq(StaticAnalysisTask::getProjectId, projectId)
                        .eq(StaticAnalysisTask::getReviewTaskId, reviewTask.getId())
                        .eq(StaticAnalysisTask::getStatus, "SUCCEEDED")
                        .orderByDesc(StaticAnalysisTask::getCreatedAt)
                        .last("LIMIT 1")
        );
        if (staticTask == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "请先为当前Diff完成静态分析");
        }
        String runningKey = projectId + ":" + reviewTask.getId();
        AiReviewTask existing = aiReviewTaskMapper.selectOne(Wrappers.lambdaQuery(AiReviewTask.class)
                .eq(AiReviewTask::getRunningKey, runningKey)
                .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        if (existing != null) {
            if (!isStale(existing, now)) {
                throw new BusinessException(ErrorCode.CONFLICT, "当前Diff已有AI审查任务正在运行");
            }
            expireStaleTask(existing, now);
        }

        AiInvocationLog invocation = new AiInvocationLog();
        invocation.setTraceId(UUID.randomUUID().toString());
        invocation.setProjectId(projectId);
        invocation.setProvider(provider);
        invocation.setModelName(modelName);
        invocation.setRequestType("CODE_REVIEW");
        invocation.setStatus("RUNNING");
        invocation.setPromptTokens(0);
        invocation.setCompletionTokens(0);
        invocation.setTotalTokens(0);
        invocation.setLatencyMs(0L);
        invocation.setPromptVersion(promptVersion);
        invocation.setCreatedAt(now);
        invocationMapper.insert(invocation);

        AiReviewTask task = new AiReviewTask();
        task.setProjectId(projectId);
        task.setReviewTaskId(reviewTask.getId());
        task.setStaticAnalysisTaskId(staticTask.getId());
        task.setInvocationId(invocation.getId());
        task.setRevision(reviewTask.getTargetRevision());
        task.setProvider(provider);
        task.setModelName(modelName);
        task.setPromptVersion(promptVersion);
        task.setExecutionMode(executionMode.name());
        task.setStatus("RUNNING");
        task.setContextChunks(0);
        task.setFindingCount(0);
        task.setRejectedFindings(0);
        task.setRunningKey(runningKey);
        task.setCreatedAt(now);
        task.setStartedAt(now);
        try {
            aiReviewTaskMapper.insert(task);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前Diff已有AI审查任务正在运行");
        }

        List<ReviewFinding> staticFindings = findingMapper.selectList(
                Wrappers.lambdaQuery(ReviewFinding.class)
                        .eq(ReviewFinding::getAnalysisTaskId, staticTask.getId())
                        .eq(ReviewFinding::getSource, "STATIC")
                        .orderByAsc(ReviewFinding::getFilePath)
                        .orderByAsc(ReviewFinding::getStartLine)
        );
        return new AiReviewContext(
                projectId, task.getId(), invocation.getId(), staticTask.getId(), reviewTask, staticFindings
        );
    }

    @Transactional
    public AiReviewResponse complete(
            AiReviewContext context,
            RetrievalSearchResponse retrieval,
            AiFindingValidationResult validation,
            AiReviewModelResult modelResult,
            long latencyMs,
            String requestHash
    ) {
        LocalDateTime now = LocalDateTime.now();
        for (ValidatedAiFinding finding : validation.findings()) {
            ReviewFinding entity = new ReviewFinding();
            entity.setProjectId(context.projectId());
            entity.setReviewTaskId(context.reviewTask().getId());
            entity.setAnalysisTaskId(context.staticAnalysisTaskId());
            entity.setAiReviewTaskId(context.aiReviewTaskId());
            entity.setChunkId(finding.evidenceChunk().chunkId());
            entity.setSource("LLM");
            entity.setRuleId("AI_" + finding.category().name());
            entity.setCategory(finding.category().name());
            entity.setSeverity(finding.severity().name());
            entity.setFilePath(truncate(finding.evidenceChunk().filePath(), 1000));
            entity.setPathHash(sha256(finding.evidenceChunk().filePath()));
            entity.setStartLine(defaultLine(finding.evidenceChunk().startLine()));
            entity.setEndLine(defaultLine(finding.evidenceChunk().endLine()));
            entity.setMessage(truncate(finding.title(), 1000));
            entity.setEvidence(finding.evidence());
            entity.setConclusionType(finding.conclusionType().name());
            entity.setConfidence(finding.confidence());
            entity.setRiskScenario(finding.riskScenario());
            entity.setSuggestion(finding.suggestion());
            entity.setVerification(finding.verification());
            entity.setFingerprint(fingerprint(context.aiReviewTaskId(), finding));
            entity.setCreatedAt(now);
            findingMapper.insert(entity);
        }

        AiReviewTask task = requireTask(context.aiReviewTaskId());
        task.setStatus("SUCCEEDED");
        task.setRetrievalConfigVersion(retrieval.configVersion());
        task.setRetrievalMode(retrieval.executedMode());
        task.setContextChunks(retrieval.hits().size());
        task.setFindingCount(validation.findings().size());
        task.setRejectedFindings(validation.rejectedCount());
        task.setRunningKey(null);
        task.setFinishedAt(now);
        aiReviewTaskMapper.updateById(task);
        clearRunningKey(task.getId());

        AiInvocationLog invocation = requireInvocation(context.invocationId());
        invocation.setStatus("SUCCEEDED");
        invocation.setPromptTokens(modelResult.promptTokens());
        invocation.setCompletionTokens(modelResult.completionTokens());
        invocation.setTotalTokens(modelResult.totalTokens());
        invocation.setLatencyMs(latencyMs);
        invocation.setRequestHash(requestHash);
        invocationMapper.updateById(invocation);
        return toResponse(task, invocation, listFindings(task.getId()));
    }

    @Transactional
    public void fail(
            AiReviewContext context,
            String errorCode,
            String errorMessage,
            long latencyMs,
            String requestHash
    ) {
        AiReviewTask task = requireTask(context.aiReviewTaskId());
        task.setStatus("FAILED");
        task.setRunningKey(null);
        task.setErrorMessage(truncate(errorMessage, 1000));
        task.setFinishedAt(LocalDateTime.now());
        aiReviewTaskMapper.updateById(task);
        clearRunningKey(task.getId());

        AiInvocationLog invocation = requireInvocation(context.invocationId());
        invocation.setStatus("FAILED");
        invocation.setLatencyMs(latencyMs);
        invocation.setErrorCode(truncate(errorCode, 64));
        invocation.setErrorMessage(truncate(errorMessage, 1000));
        invocation.setRequestHash(requestHash);
        invocationMapper.updateById(invocation);
    }

    @Transactional(readOnly = true)
    public AiReviewResponse getLatest(Long projectId) {
        if (projectMapper.selectById(projectId) == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "项目不存在");
        }
        AiReviewTask task = aiReviewTaskMapper.selectOne(
                Wrappers.lambdaQuery(AiReviewTask.class)
                        .eq(AiReviewTask::getProjectId, projectId)
                        .orderByDesc(AiReviewTask::getCreatedAt)
                        .last("LIMIT 1")
        );
        if (task == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "项目暂无AI审查任务");
        }
        return toResponse(task, requireInvocation(task.getInvocationId()), listFindings(task.getId()));
    }

    private List<ReviewFinding> listFindings(Long aiReviewTaskId) {
        return findingMapper.selectList(Wrappers.lambdaQuery(ReviewFinding.class)
                .eq(ReviewFinding::getAiReviewTaskId, aiReviewTaskId)
                .orderByDesc(ReviewFinding::getSeverity)
                .orderByAsc(ReviewFinding::getFilePath)
                .orderByAsc(ReviewFinding::getStartLine));
    }

    private AiReviewResponse toResponse(
            AiReviewTask task,
            AiInvocationLog invocation,
            List<ReviewFinding> findings
    ) {
        Map<Long, CodeReviewFeedback> feedbackByFindingId = listFeedbackByFindingId(findings);
        return new AiReviewResponse(
                task.getId(), task.getProjectId(), task.getReviewTaskId(), task.getStaticAnalysisTaskId(),
                task.getInvocationId(), task.getRevision(), task.getProvider(), task.getModelName(),
                task.getPromptVersion(), task.getExecutionMode(), task.getRetrievalConfigVersion(), task.getRetrievalMode(),
                task.getStatus(), task.getContextChunks(), task.getFindingCount(), task.getRejectedFindings(),
                invocation.getPromptTokens(), invocation.getCompletionTokens(), invocation.getTotalTokens(),
                invocation.getLatencyMs(), task.getErrorMessage(), task.getCreatedAt(), task.getFinishedAt(),
                findings.stream()
                        .map(finding -> toFindingResponse(
                                finding,
                                feedbackByFindingId.get(finding.getId())
                        ))
                        .toList(),
                listToolCalls(task.getInvocationId())
        );
    }

    private Map<Long, CodeReviewFeedback> listFeedbackByFindingId(List<ReviewFinding> findings) {
        if (findings.isEmpty()) {
            return Map.of();
        }
        List<Long> findingIds = findings.stream().map(ReviewFinding::getId).toList();
        return feedbackMapper.selectList(
                        Wrappers.lambdaQuery(CodeReviewFeedback.class)
                                .in(CodeReviewFeedback::getFindingId, findingIds)
                ).stream()
                .collect(Collectors.toMap(
                        CodeReviewFeedback::getFindingId,
                        Function.identity()
                ));
    }

    private List<ToolCallResponse> listToolCalls(Long invocationId) {
        return toolCallLogMapper.selectList(Wrappers.lambdaQuery(ToolCallLog.class)
                        .eq(ToolCallLog::getInvocationId, invocationId)
                        .orderByAsc(ToolCallLog::getStepNo)
                        .orderByAsc(ToolCallLog::getId))
                .stream().map(ToolCallResponse::from).toList();
    }

    private AiReviewFindingResponse toFindingResponse(
            ReviewFinding finding,
            CodeReviewFeedback feedback
    ) {
        return new AiReviewFindingResponse(
                finding.getId(), finding.getChunkId(), finding.getSource(), finding.getCategory(),
                finding.getSeverity(), finding.getConclusionType(), finding.getConfidence(),
                finding.getFilePath(), finding.getStartLine(), finding.getEndLine(), finding.getMessage(),
                finding.getEvidence(), finding.getRiskScenario(), finding.getSuggestion(),
                finding.getVerification(),
                feedback == null ? null : ReviewFeedbackResponse.from(feedback)
        );
    }

    private AiReviewTask requireTask(Long id) {
        AiReviewTask task = aiReviewTaskMapper.selectById(id);
        if (task == null) {
            throw new IllegalStateException("AI审查任务不存在");
        }
        return task;
    }

    private void clearRunningKey(Long taskId) {
        aiReviewTaskMapper.update(
                null,
                Wrappers.lambdaUpdate(AiReviewTask.class)
                        .eq(AiReviewTask::getId, taskId)
                        .set(AiReviewTask::getRunningKey, null)
        );
    }

    private AiInvocationLog requireInvocation(Long id) {
        AiInvocationLog invocation = invocationMapper.selectById(id);
        if (invocation == null) {
            throw new IllegalStateException("AI调用日志不存在");
        }
        return invocation;
    }

    private String fingerprint(Long aiReviewTaskId, ValidatedAiFinding finding) {
        return sha256(String.join("\n",
                String.valueOf(aiReviewTaskId),
                String.valueOf(finding.evidenceChunk().chunkId()),
                finding.category().name(),
                finding.title()
        ));
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前JDK不支持SHA-256", exception);
        }
    }

    private int defaultLine(Integer line) {
        return line == null || line < 1 ? 1 : line;
    }

    private String truncate(String value, int maxLength) {
        String normalized = StringUtils.hasText(value) ? value.trim() : "AI审查执行失败";
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private boolean isStale(AiReviewTask task, LocalDateTime now) {
        LocalDateTime startedAt = task.getStartedAt() == null ? task.getCreatedAt() : task.getStartedAt();
        return startedAt != null && startedAt.isBefore(now.minus(properties.getStaleTaskTimeout()));
    }

    private void expireStaleTask(AiReviewTask task, LocalDateTime now) {
        task.setStatus("FAILED");
        task.setErrorMessage("AI审查任务因服务中断或超时自动结束");
        task.setFinishedAt(now);
        aiReviewTaskMapper.updateById(task);
        clearRunningKey(task.getId());

        AiInvocationLog invocation = requireInvocation(task.getInvocationId());
        invocation.setStatus("FAILED");
        invocation.setErrorCode("STALE_TASK");
        invocation.setErrorMessage("AI审查任务因服务中断或超时自动结束");
        invocationMapper.updateById(invocation);
    }
}
