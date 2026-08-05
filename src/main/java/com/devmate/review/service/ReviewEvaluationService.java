package com.devmate.review.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devmate.agent.model.AiInvocationMetrics;
import com.devmate.agent.service.AiInvocationMetricsService;
import com.devmate.common.error.BusinessException;
import com.devmate.common.error.ErrorCode;
import com.devmate.project.service.ProjectService;
import com.devmate.review.config.ReviewEvaluationProperties;
import com.devmate.review.dto.ReviewEvaluationItemResultResponse;
import com.devmate.review.dto.ReviewEvaluationRunResponse;
import com.devmate.review.dto.RunReviewEvaluationRequest;
import com.devmate.review.entity.AiReviewTask;
import com.devmate.review.entity.CodeReviewTask;
import com.devmate.review.entity.ReviewEvaluationCase;
import com.devmate.review.entity.ReviewEvaluationRun;
import com.devmate.review.entity.ReviewFinding;
import com.devmate.review.mapper.AiReviewTaskMapper;
import com.devmate.review.mapper.CodeReviewTaskMapper;
import com.devmate.review.mapper.ReviewEvaluationRunMapper;
import com.devmate.review.mapper.ReviewFindingMapper;
import com.devmate.review.model.ReviewEvaluationCalculation;
import com.devmate.tool.model.ToolCallMetrics;
import com.devmate.tool.service.ToolCallMetricsService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReviewEvaluationService {

    private final ProjectService projectService;
    private final CodeReviewTaskMapper reviewTaskMapper;
    private final AiReviewTaskMapper aiReviewTaskMapper;
    private final ReviewFindingMapper findingMapper;
    private final ReviewEvaluationRunMapper runMapper;
    private final ReviewEvaluationCaseService caseService;
    private final ReviewFindingMatcher matcher;
    private final AiInvocationMetricsService invocationMetricsService;
    private final ToolCallMetricsService toolCallMetricsService;
    private final ReviewEvaluationProperties properties;
    private final ObjectMapper objectMapper;

    public ReviewEvaluationService(
            ProjectService projectService,
            CodeReviewTaskMapper reviewTaskMapper,
            AiReviewTaskMapper aiReviewTaskMapper,
            ReviewFindingMapper findingMapper,
            ReviewEvaluationRunMapper runMapper,
            ReviewEvaluationCaseService caseService,
            ReviewFindingMatcher matcher,
            AiInvocationMetricsService invocationMetricsService,
            ToolCallMetricsService toolCallMetricsService,
            ReviewEvaluationProperties properties,
            ObjectMapper objectMapper
    ) {
        this.projectService = projectService;
        this.reviewTaskMapper = reviewTaskMapper;
        this.aiReviewTaskMapper = aiReviewTaskMapper;
        this.findingMapper = findingMapper;
        this.runMapper = runMapper;
        this.caseService = caseService;
        this.matcher = matcher;
        this.invocationMetricsService = invocationMetricsService;
        this.toolCallMetricsService = toolCallMetricsService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ReviewEvaluationRunResponse run(
            Long projectId,
            RunReviewEvaluationRequest request
    ) {
        projectService.getProject(projectId);
        AiReviewTask aiTask = requireSuccessfulAiTask(projectId, request.aiReviewTaskId());
        CodeReviewTask reviewTask = requireReviewTask(projectId, aiTask.getReviewTaskId());
        String datasetVersion = request.datasetVersion().trim();
        List<ReviewEvaluationCase> cases = caseService.loadEnabledCases(
                projectId, datasetVersion, reviewTask.getId()
        );
        if (cases.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "当前Diff没有启用的审查评测用例");
        }
        if (cases.stream().anyMatch(value -> !aiTask.getRevision().equals(value.getTargetRevision()))) {
            throw new BusinessException(ErrorCode.CONFLICT, "评测用例revision与AI审查任务不一致");
        }

        String datasetHash = datasetHash(cases);
        ReviewEvaluationRun existing = findSnapshot(aiTask.getId(), datasetHash);
        if (existing != null) {
            return toResponse(existing);
        }

        List<ReviewFinding> findings = findingMapper.selectList(
                Wrappers.lambdaQuery(ReviewFinding.class)
                        .eq(ReviewFinding::getProjectId, projectId)
                        .eq(ReviewFinding::getAiReviewTaskId, aiTask.getId())
                        .eq(ReviewFinding::getSource, "LLM")
                        .orderByAsc(ReviewFinding::getFilePath)
                        .orderByAsc(ReviewFinding::getStartLine)
                        .orderByAsc(ReviewFinding::getId)
        );
        ReviewEvaluationCalculation calculation = matcher.calculate(cases, findings);
        AiInvocationMetrics invocationMetrics = invocationMetricsService.requireMetrics(
                aiTask.getInvocationId(), projectId
        );
        ToolCallMetrics toolMetrics = toolCallMetricsService.metrics(
                aiTask.getInvocationId(), projectId
        );

        LocalDateTime now = LocalDateTime.now();
        ReviewEvaluationRun run = new ReviewEvaluationRun();
        run.setProjectId(projectId);
        run.setReviewTaskId(reviewTask.getId());
        run.setAiReviewTaskId(aiTask.getId());
        run.setDatasetVersion(datasetVersion);
        run.setDatasetHash(datasetHash);
        run.setExecutionMode(aiTask.getExecutionMode());
        run.setRevision(aiTask.getRevision());
        run.setModelName(aiTask.getModelName());
        run.setPromptVersion(aiTask.getPromptVersion());
        run.setRetrievalConfigVersion(aiTask.getRetrievalConfigVersion());
        run.setStatus("SUCCEEDED");
        run.setExpectedDefects(calculation.expectedDefects());
        run.setPredictedFindings(calculation.predictedFindings());
        run.setTruePositives(calculation.truePositives());
        run.setFalsePositives(calculation.falsePositives());
        run.setFalseNegatives(calculation.falseNegatives());
        run.setManualReviewCount(calculation.manualReviewCount());
        run.setPartialMetrics(calculation.partialMetrics() ? 1 : 0);
        run.setPrecisionScore(calculation.precision());
        run.setRecallScore(calculation.recall());
        run.setF1Score(calculation.f1());
        run.setTotalTokens(invocationMetrics.totalTokens());
        run.setLatencyMs(invocationMetrics.latencyMs());
        run.setToolCallCount(toolMetrics.totalCalls());
        run.setToolSuccessCount(toolMetrics.successfulCalls());
        run.setResultJson(writeResults(calculation.results()));
        run.setCreatedAt(now);
        run.setFinishedAt(now);
        try {
            runMapper.insert(run);
        } catch (DuplicateKeyException exception) {
            ReviewEvaluationRun concurrent = findSnapshot(aiTask.getId(), datasetHash);
            if (concurrent != null) {
                return toResponse(concurrent);
            }
            throw exception;
        }
        return ReviewEvaluationRunResponse.from(run, calculation.results());
    }

    @Transactional(readOnly = true)
    public List<ReviewEvaluationRunResponse> list(
            Long projectId,
            String datasetVersion,
            Long reviewTaskId
    ) {
        projectService.getProject(projectId);
        requireReviewTask(projectId, reviewTaskId);
        return runMapper.selectList(Wrappers.lambdaQuery(ReviewEvaluationRun.class)
                        .eq(ReviewEvaluationRun::getProjectId, projectId)
                        .eq(ReviewEvaluationRun::getDatasetVersion, datasetVersion.trim())
                        .eq(ReviewEvaluationRun::getReviewTaskId, reviewTaskId)
                        .orderByDesc(ReviewEvaluationRun::getCreatedAt)
                        .orderByDesc(ReviewEvaluationRun::getId)
                        .last("LIMIT " + properties.getMaxRunsReturned()))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private AiReviewTask requireSuccessfulAiTask(Long projectId, Long taskId) {
        AiReviewTask task = aiReviewTaskMapper.selectById(taskId);
        if (task == null || !projectId.equals(task.getProjectId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "AI审查任务不存在");
        }
        if (!"SUCCEEDED".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "只能评测成功的AI审查任务");
        }
        if (!StringUtils.hasText(task.getExecutionMode())) {
            throw new IllegalStateException("AI审查任务缺少执行模式");
        }
        return task;
    }

    private CodeReviewTask requireReviewTask(Long projectId, Long reviewTaskId) {
        CodeReviewTask task = reviewTaskMapper.selectById(reviewTaskId);
        if (task == null || !projectId.equals(task.getProjectId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Diff任务不存在");
        }
        return task;
    }

    private ReviewEvaluationRun findSnapshot(Long aiReviewTaskId, String datasetHash) {
        return runMapper.selectOne(Wrappers.lambdaQuery(ReviewEvaluationRun.class)
                .eq(ReviewEvaluationRun::getAiReviewTaskId, aiReviewTaskId)
                .eq(ReviewEvaluationRun::getDatasetHash, datasetHash)
                .last("LIMIT 1"));
    }

    private String datasetHash(List<ReviewEvaluationCase> cases) {
        String canonical = cases.stream()
                .map(value -> String.join("\u001f",
                        value.getCaseKey(), value.getTargetRevision(), value.getExpectationType(),
                        nullToEmpty(value.getCategory()), nullToEmpty(value.getFilePath()),
                        nullToEmpty(value.getStartLine()), nullToEmpty(value.getEndLine()),
                        value.getRationale()
                ))
                .collect(Collectors.joining("\u001e"));
        return sha256(canonical);
    }

    private String nullToEmpty(Object value) {
        return value == null ? "" : value.toString();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前JDK不支持SHA-256", exception);
        }
    }

    private String writeResults(List<ReviewEvaluationItemResultResponse> results) {
        try {
            return objectMapper.writeValueAsString(results);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("保存代码审查评测结果失败", exception);
        }
    }

    private List<ReviewEvaluationItemResultResponse> readResults(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("读取代码审查评测结果失败", exception);
        }
    }

    private ReviewEvaluationRunResponse toResponse(ReviewEvaluationRun run) {
        return ReviewEvaluationRunResponse.from(run, readResults(run.getResultJson()));
    }
}
