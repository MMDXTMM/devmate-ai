package com.devmate.review.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devmate.common.error.BusinessException;
import com.devmate.common.error.ErrorCode;
import com.devmate.project.service.ProjectService;
import com.devmate.review.config.ReviewEvaluationProperties;
import com.devmate.review.dto.CreateReviewEvaluationCaseRequest;
import com.devmate.review.dto.MappedSymbolResponse;
import com.devmate.review.dto.ReviewEvaluationCaseResponse;
import com.devmate.review.dto.ReviewFileResponse;
import com.devmate.review.entity.CodeReviewTask;
import com.devmate.review.entity.ReviewEvaluationCase;
import com.devmate.review.entity.ReviewEvaluationRun;
import com.devmate.review.mapper.CodeReviewTaskMapper;
import com.devmate.review.mapper.ReviewEvaluationCaseMapper;
import com.devmate.review.mapper.ReviewEvaluationRunMapper;
import com.devmate.review.model.LineRange;
import com.devmate.review.model.ReviewExpectationType;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

@Service
public class ReviewEvaluationCaseService {

    private final ProjectService projectService;
    private final CodeReviewTaskMapper reviewTaskMapper;
    private final ReviewEvaluationCaseMapper caseMapper;
    private final ReviewEvaluationRunMapper runMapper;
    private final ReviewEvaluationProperties properties;
    private final ReviewDiffStateService reviewDiffStateService;

    public ReviewEvaluationCaseService(
            ProjectService projectService,
            CodeReviewTaskMapper reviewTaskMapper,
            ReviewEvaluationCaseMapper caseMapper,
            ReviewEvaluationRunMapper runMapper,
            ReviewEvaluationProperties properties,
            ReviewDiffStateService reviewDiffStateService
    ) {
        this.projectService = projectService;
        this.reviewTaskMapper = reviewTaskMapper;
        this.caseMapper = caseMapper;
        this.runMapper = runMapper;
        this.properties = properties;
        this.reviewDiffStateService = reviewDiffStateService;
    }

    @Transactional
    public ReviewEvaluationCaseResponse create(
            Long projectId,
            CreateReviewEvaluationCaseRequest request
    ) {
        projectService.getProject(projectId);
        CodeReviewTask reviewTask = requireSuccessfulReviewTask(projectId, request.reviewTaskId());
        String datasetVersion = request.datasetVersion().trim();
        if (runMapper.selectCount(Wrappers.lambdaQuery(ReviewEvaluationRun.class)
                .eq(ReviewEvaluationRun::getProjectId, projectId)
                .eq(ReviewEvaluationRun::getDatasetVersion, datasetVersion)
                .eq(ReviewEvaluationRun::getReviewTaskId, reviewTask.getId())) > 0) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "该评测集版本已经产生运行记录，请创建新版本后再增加用例"
            );
        }
        List<ReviewEvaluationCase> existingCases = loadEnabledCases(
                projectId, datasetVersion, reviewTask.getId()
        );
        if (existingCases.size() >= properties.getMaxCasesPerReview()) {
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT,
                    "单个Diff的启用评测用例不能超过" + properties.getMaxCasesPerReview() + "个"
            );
        }
        validateExpectation(request, existingCases);

        LocalDateTime now = LocalDateTime.now();
        ReviewEvaluationCase value = new ReviewEvaluationCase();
        value.setProjectId(projectId);
        value.setReviewTaskId(reviewTask.getId());
        value.setDatasetVersion(datasetVersion);
        value.setCaseKey(request.caseKey().trim());
        value.setName(request.name().trim());
        value.setTargetRevision(reviewTask.getTargetRevision());
        value.setExpectationType(request.expectationType().name());
        value.setRationale(request.rationale().trim());
        value.setEnabled(1);
        value.setCreatedAt(now);
        value.setUpdatedAt(now);
        if (request.expectationType() == ReviewExpectationType.DEFECT) {
            String filePath = normalizeRelativePath(request.filePath());
            ReviewFileResponse targetFile = requireTargetDiffEvidence(
                    projectId,
                    reviewTask,
                    filePath,
                    request.startLine(),
                    request.endLine()
            );
            filePath = targetFile.newPath();
            value.setCategory(request.category().name());
            value.setFilePath(filePath);
            value.setPathHash(sha256(filePath));
            value.setStartLine(request.startLine());
            value.setEndLine(request.endLine());
        }
        try {
            caseMapper.insert(value);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "同一评测集中的用例键不能重复"
            );
        }
        return ReviewEvaluationCaseResponse.from(value);
    }

    @Transactional(readOnly = true)
    public List<ReviewEvaluationCaseResponse> list(
            Long projectId,
            String datasetVersion,
            Long reviewTaskId
    ) {
        projectService.getProject(projectId);
        if (reviewTaskId == null) {
            return loadEnabledDatasetCases(projectId, datasetVersion.trim()).stream()
                    .map(ReviewEvaluationCaseResponse::from)
                    .toList();
        }
        requireReviewTask(projectId, reviewTaskId);
        return loadEnabledCases(projectId, datasetVersion.trim(), reviewTaskId).stream()
                .map(ReviewEvaluationCaseResponse::from)
                .toList();
    }

    private List<ReviewEvaluationCase> loadEnabledDatasetCases(
            Long projectId,
            String datasetVersion
    ) {
        List<ReviewEvaluationCase> values = caseMapper.selectList(
                Wrappers.lambdaQuery(ReviewEvaluationCase.class)
                        .eq(ReviewEvaluationCase::getProjectId, projectId)
                        .eq(ReviewEvaluationCase::getDatasetVersion, datasetVersion)
                        .eq(ReviewEvaluationCase::getEnabled, 1)
                        .orderByAsc(ReviewEvaluationCase::getCaseKey)
                        .last("LIMIT " + (properties.getMaxCasesPerReview() + 1))
        );
        if (values.size() > properties.getMaxCasesPerReview()) {
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT,
                    "单个项目评测集的启用用例不能超过" + properties.getMaxCasesPerReview() + "个"
            );
        }
        return values;
    }

    @Transactional(readOnly = true)
    public List<ReviewEvaluationCase> loadEnabledCases(
            Long projectId,
            String datasetVersion,
            Long reviewTaskId
    ) {
        List<ReviewEvaluationCase> values = caseMapper.selectList(
                Wrappers.lambdaQuery(ReviewEvaluationCase.class)
                        .eq(ReviewEvaluationCase::getProjectId, projectId)
                        .eq(ReviewEvaluationCase::getDatasetVersion, datasetVersion)
                        .eq(ReviewEvaluationCase::getReviewTaskId, reviewTaskId)
                        .eq(ReviewEvaluationCase::getEnabled, 1)
                        .orderByAsc(ReviewEvaluationCase::getCaseKey)
                        .last("LIMIT " + (properties.getMaxCasesPerReview() + 1))
        );
        if (values.size() > properties.getMaxCasesPerReview()) {
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT,
                    "单个Diff的启用评测用例不能超过" + properties.getMaxCasesPerReview() + "个"
            );
        }
        return values;
    }

    private void validateExpectation(
            CreateReviewEvaluationCaseRequest request,
            List<ReviewEvaluationCase> existingCases
    ) {
        boolean hasClean = existingCases.stream()
                .anyMatch(value -> ReviewExpectationType.CLEAN.name().equals(value.getExpectationType()));
        if (request.expectationType() == ReviewExpectationType.CLEAN) {
            if (request.category() != null || StringUtils.hasText(request.filePath())
                    || request.startLine() != null || request.endLine() != null) {
                throw new BusinessException(
                        ErrorCode.INVALID_ARGUMENT,
                        "无缺陷用例不能填写缺陷类别、文件或行范围"
                );
            }
            if (!existingCases.isEmpty()) {
                throw new BusinessException(
                        ErrorCode.CONFLICT,
                        "同一Diff和数据集不能混用无缺陷与缺陷用例"
                );
            }
            return;
        }
        if (hasClean) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "同一Diff和数据集不能混用无缺陷与缺陷用例"
            );
        }
        if (request.category() == null || !StringUtils.hasText(request.filePath())
                || request.startLine() == null || request.endLine() == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT,
                    "缺陷用例必须填写类别、文件路径和行范围"
            );
        }
        if (request.endLine() < request.startLine()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "结束行不能小于起始行");
        }
    }

    private CodeReviewTask requireSuccessfulReviewTask(Long projectId, Long reviewTaskId) {
        CodeReviewTask task = requireReviewTask(projectId, reviewTaskId);
        if (!"SUCCEEDED".equals(task.getStatus()) || !StringUtils.hasText(task.getTargetRevision())) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "只能为成功的Diff任务建立评测用例");
        }
        return task;
    }

    private ReviewFileResponse requireTargetDiffEvidence(
            Long projectId,
            CodeReviewTask reviewTask,
            String filePath,
            int startLine,
            int endLine
    ) {
        List<ReviewFileResponse> matchingFiles = reviewDiffStateService.findTargetFiles(
                projectId,
                reviewTask.getId(),
                filePath
        );
        if (matchingFiles.size() != 1) {
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT,
                    "缺陷文件不属于指定Diff的目标版本"
            );
        }

        ReviewFileResponse file = matchingFiles.getFirst();
        List<LineRange> changedLines = file.changedLines() == null
                ? List.of()
                : file.changedLines();
        boolean intersectsChangedLine = changedLines.stream()
                .anyMatch(range -> intersects(startLine, endLine, range.startLine(), range.endLine()));
        if (!intersectsChangedLine) {
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT,
                    "缺陷行范围必须与目标版本变更行相交"
            );
        }

        List<MappedSymbolResponse> mappedSymbols = file.mappedSymbols() == null
                ? List.of()
                : file.mappedSymbols();
        boolean hasTargetEvidence = changedLines.stream().anyMatch(changedLine ->
                mappedSymbols.stream().anyMatch(symbol ->
                        isPersistedTargetSymbol(symbol)
                                && hasCommonLine(
                                        startLine,
                                        endLine,
                                        changedLine.startLine(),
                                        changedLine.endLine(),
                                        symbol.startLine(),
                                        symbol.endLine()
                        )
                )
        );
        if (!hasTargetEvidence) {
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT,
                    "缺陷行范围缺少持久化的TARGET代码证据"
            );
        }
        return file;
    }

    private boolean isPersistedTargetSymbol(MappedSymbolResponse symbol) {
        return symbol != null
                && symbol.chunkId() != null
                && symbol.chunkId() > 0
                && "TARGET".equals(symbol.revisionSide())
                && symbol.startLine() != null
                && symbol.endLine() != null
                && symbol.startLine() > 0
                && symbol.endLine() >= symbol.startLine();
    }

    private boolean intersects(int leftStart, int leftEnd, int rightStart, int rightEnd) {
        return leftStart <= leftEnd
                && rightStart <= rightEnd
                && leftStart <= rightEnd
                && leftEnd >= rightStart;
    }

    private boolean hasCommonLine(
            int caseStart,
            int caseEnd,
            int changedStart,
            int changedEnd,
            int symbolStart,
            int symbolEnd
    ) {
        int overlapStart = Math.max(caseStart, Math.max(changedStart, symbolStart));
        int overlapEnd = Math.min(caseEnd, Math.min(changedEnd, symbolEnd));
        return overlapStart <= overlapEnd;
    }

    private CodeReviewTask requireReviewTask(Long projectId, Long reviewTaskId) {
        CodeReviewTask task = reviewTaskMapper.selectById(reviewTaskId);
        if (task == null || !projectId.equals(task.getProjectId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Diff任务不存在");
        }
        return task;
    }

    private String normalizeRelativePath(String value) {
        String input = value.trim().replace('\\', '/');
        final Path normalized;
        try {
            normalized = Path.of(input).normalize();
        } catch (InvalidPathException exception) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "缺陷文件路径格式不合法");
        }
        String result = normalized.toString().replace('\\', '/');
        if (!StringUtils.hasText(result) || normalized.isAbsolute() || normalized.startsWith("..")
                || result.matches("^[A-Za-z]:.*")) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "缺陷文件路径必须是项目内相对路径");
        }
        return result;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前JDK不支持SHA-256", exception);
        }
    }
}
