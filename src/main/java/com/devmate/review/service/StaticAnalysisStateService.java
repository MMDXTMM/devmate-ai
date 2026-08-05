package com.devmate.review.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devmate.common.error.BusinessException;
import com.devmate.common.error.ErrorCode;
import com.devmate.project.mapper.ProjectMapper;
import com.devmate.review.dto.StaticAnalysisResponse;
import com.devmate.review.dto.StaticFindingResponse;
import com.devmate.review.entity.CodeReviewFile;
import com.devmate.review.entity.CodeReviewTask;
import com.devmate.review.entity.ReviewFinding;
import com.devmate.review.entity.StaticAnalysisTask;
import com.devmate.review.mapper.CodeReviewFileMapper;
import com.devmate.review.mapper.CodeReviewTaskMapper;
import com.devmate.review.mapper.ReviewFindingMapper;
import com.devmate.review.mapper.StaticAnalysisTaskMapper;
import com.devmate.review.model.LineRange;
import com.devmate.review.model.StaticAnalysisResult;
import com.devmate.review.model.StaticFinding;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StaticAnalysisStateService {

    private final ProjectMapper projectMapper;
    private final CodeReviewTaskMapper reviewTaskMapper;
    private final CodeReviewFileMapper reviewFileMapper;
    private final StaticAnalysisTaskMapper analysisTaskMapper;
    private final ReviewFindingMapper findingMapper;
    private final ObjectMapper objectMapper;

    public StaticAnalysisStateService(
            ProjectMapper projectMapper,
            CodeReviewTaskMapper reviewTaskMapper,
            CodeReviewFileMapper reviewFileMapper,
            StaticAnalysisTaskMapper analysisTaskMapper,
            ReviewFindingMapper findingMapper,
            ObjectMapper objectMapper
    ) {
        this.projectMapper = projectMapper;
        this.reviewTaskMapper = reviewTaskMapper;
        this.reviewFileMapper = reviewFileMapper;
        this.analysisTaskMapper = analysisTaskMapper;
        this.findingMapper = findingMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public StaticAnalysisContext prepare(Long projectId, String toolName, String toolVersion) {
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
        List<StaticAnalysisFileContext> files = reviewFileMapper.selectList(
                        Wrappers.lambdaQuery(CodeReviewFile.class)
                                .eq(CodeReviewFile::getReviewTaskId, reviewTask.getId())
                                .orderByAsc(CodeReviewFile::getId)
                ).stream()
                .filter(file -> file.getNewPath() != null && file.getNewPath().endsWith(".java"))
                .filter(file -> !"SKIPPED".equals(file.getCoverageStatus()))
                .map(file -> new StaticAnalysisFileContext(
                        file.getNewPath(),
                        readRanges(file.getChangedLinesJson())
                ))
                .filter(file -> !file.changedLines().isEmpty())
                .toList();

        LocalDateTime now = LocalDateTime.now();
        StaticAnalysisTask task = new StaticAnalysisTask();
        task.setProjectId(projectId);
        task.setReviewTaskId(reviewTask.getId());
        task.setToolName(toolName);
        task.setToolVersion(toolVersion);
        task.setStatus("RUNNING");
        task.setAnalyzedFiles(0);
        task.setFindingCount(0);
        task.setCreatedAt(now);
        task.setStartedAt(now);
        analysisTaskMapper.insert(task);
        return new StaticAnalysisContext(
                projectId,
                task.getId(),
                reviewTask.getId(),
                reviewTask.getIndexTaskId(),
                reviewTask.getTargetRevision(),
                files
        );
    }

    @Transactional
    public StaticAnalysisResponse complete(StaticAnalysisContext context, StaticAnalysisResult result) {
        LocalDateTime now = LocalDateTime.now();
        Map<String, StaticFinding> uniqueFindings = new LinkedHashMap<>();
        for (StaticFinding finding : result.findings()) {
            uniqueFindings.putIfAbsent(fingerprint(finding), finding);
        }
        for (Map.Entry<String, StaticFinding> entry : uniqueFindings.entrySet()) {
            StaticFinding finding = entry.getValue();
            ReviewFinding entity = new ReviewFinding();
            entity.setProjectId(context.projectId());
            entity.setReviewTaskId(context.reviewTaskId());
            entity.setAnalysisTaskId(context.analysisTaskId());
            entity.setSource("STATIC");
            entity.setRuleId(truncate(finding.ruleId(), 255));
            entity.setCategory(truncate(finding.category(), 64));
            entity.setSeverity(finding.severity());
            entity.setFilePath(truncate(finding.filePath(), 1000));
            entity.setPathHash(sha256(finding.filePath()));
            entity.setStartLine(finding.startLine());
            entity.setEndLine(finding.endLine());
            entity.setMessage(truncate(finding.message(), 1000));
            entity.setEvidence(finding.evidence());
            entity.setFingerprint(entry.getKey());
            entity.setCreatedAt(now);
            findingMapper.insert(entity);
        }

        StaticAnalysisTask task = requireTask(context.analysisTaskId());
        task.setStatus("SUCCEEDED");
        task.setAnalyzedFiles(result.analyzedFiles());
        task.setFindingCount(uniqueFindings.size());
        task.setFinishedAt(now);
        analysisTaskMapper.updateById(task);
        return toResponse(task, listFindings(task.getId()));
    }

    @Transactional
    public void fail(StaticAnalysisContext context, String errorMessage) {
        StaticAnalysisTask task = requireTask(context.analysisTaskId());
        task.setStatus("FAILED");
        task.setErrorMessage(truncate(errorMessage, 1000));
        task.setFinishedAt(LocalDateTime.now());
        analysisTaskMapper.updateById(task);
    }

    @Transactional(readOnly = true)
    public StaticAnalysisResponse getLatest(Long projectId) {
        if (projectMapper.selectById(projectId) == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "项目不存在");
        }
        StaticAnalysisTask task = analysisTaskMapper.selectOne(
                Wrappers.lambdaQuery(StaticAnalysisTask.class)
                        .eq(StaticAnalysisTask::getProjectId, projectId)
                        .orderByDesc(StaticAnalysisTask::getCreatedAt)
                        .last("LIMIT 1")
        );
        if (task == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "项目暂无静态分析任务");
        }
        return toResponse(task, listFindings(task.getId()));
    }

    @Transactional(readOnly = true)
    public StaticAnalysisResponse getByTask(Long projectId, Long taskId) {
        if (projectMapper.selectById(projectId) == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "项目不存在");
        }
        StaticAnalysisTask task = analysisTaskMapper.selectOne(
                Wrappers.lambdaQuery(StaticAnalysisTask.class)
                        .eq(StaticAnalysisTask::getId, taskId)
                        .eq(StaticAnalysisTask::getProjectId, projectId)
                        .last("LIMIT 1")
        );
        if (task == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "静态分析任务不存在");
        }
        return toResponse(task, listFindings(task.getId()));
    }

    private List<ReviewFinding> listFindings(Long analysisTaskId) {
        return findingMapper.selectList(Wrappers.lambdaQuery(ReviewFinding.class)
                .eq(ReviewFinding::getAnalysisTaskId, analysisTaskId)
                .orderByAsc(ReviewFinding::getFilePath)
                .orderByAsc(ReviewFinding::getStartLine));
    }

    private StaticAnalysisResponse toResponse(StaticAnalysisTask task, List<ReviewFinding> findings) {
        return new StaticAnalysisResponse(
                task.getId(), task.getProjectId(), task.getReviewTaskId(), task.getToolName(),
                task.getToolVersion(), task.getStatus(), task.getAnalyzedFiles(), task.getFindingCount(),
                task.getErrorMessage(), task.getCreatedAt(), task.getFinishedAt(),
                findings.stream().map(this::toFindingResponse).toList()
        );
    }

    private StaticFindingResponse toFindingResponse(ReviewFinding finding) {
        return new StaticFindingResponse(
                finding.getId(), finding.getSource(), finding.getRuleId(), finding.getCategory(),
                finding.getSeverity(), finding.getFilePath(), finding.getStartLine(), finding.getEndLine(),
                finding.getMessage(), finding.getEvidence()
        );
    }

    private List<LineRange> readRanges(String json) {
        try {
            return objectMapper.readValue(
                    StringUtils.hasText(json) ? json : "[]",
                    new TypeReference<>() {}
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("读取Diff目标行区间失败", exception);
        }
    }

    private StaticAnalysisTask requireTask(Long taskId) {
        StaticAnalysisTask task = analysisTaskMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalStateException("静态分析任务不存在");
        }
        return task;
    }

    private String fingerprint(StaticFinding finding) {
        String value = String.join("\n", finding.ruleId(), finding.filePath(),
                String.valueOf(finding.startLine()), String.valueOf(finding.endLine()), finding.message());
        return sha256(value);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前JDK不支持SHA-256", exception);
        }
    }

    private String truncate(String value, int maxLength) {
        String normalized = StringUtils.hasText(value) ? value.trim() : "未知静态分析错误";
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
