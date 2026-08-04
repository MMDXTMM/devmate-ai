package com.devmate.knowledge.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devmate.common.error.BusinessException;
import com.devmate.common.error.ErrorCode;
import com.devmate.knowledge.config.RetrievalProperties;
import com.devmate.knowledge.dto.CreateRetrievalEvaluationCaseRequest;
import com.devmate.knowledge.dto.RetrievalEvaluationCaseResponse;
import com.devmate.knowledge.dto.RetrievalEvaluationCaseResultResponse;
import com.devmate.knowledge.dto.RetrievalEvaluationRunResponse;
import com.devmate.knowledge.dto.RetrievalSearchResponse;
import com.devmate.knowledge.entity.KnowledgeChunk;
import com.devmate.knowledge.entity.KnowledgeDocument;
import com.devmate.knowledge.entity.RetrievalEvaluationCase;
import com.devmate.knowledge.entity.RetrievalEvaluationRun;
import com.devmate.knowledge.mapper.KnowledgeChunkMapper;
import com.devmate.knowledge.mapper.KnowledgeDocumentMapper;
import com.devmate.knowledge.mapper.RetrievalEvaluationCaseMapper;
import com.devmate.knowledge.mapper.RetrievalEvaluationRunMapper;
import com.devmate.knowledge.retrieval.ContextRetrievalService;
import com.devmate.knowledge.retrieval.RetrievalSearchCommand;
import com.devmate.project.dto.ProjectResponse;
import com.devmate.project.service.ProjectService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.nio.file.InvalidPathException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class RetrievalEvaluationService {

    private final ProjectService projectService;
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final RetrievalEvaluationCaseMapper caseMapper;
    private final RetrievalEvaluationRunMapper runMapper;
    private final ContextRetrievalService retrievalService;
    private final RetrievalProperties properties;
    private final ObjectMapper objectMapper;

    public RetrievalEvaluationService(
            ProjectService projectService,
            KnowledgeDocumentMapper documentMapper,
            KnowledgeChunkMapper chunkMapper,
            RetrievalEvaluationCaseMapper caseMapper,
            RetrievalEvaluationRunMapper runMapper,
            ContextRetrievalService retrievalService,
            RetrievalProperties properties,
            ObjectMapper objectMapper
    ) {
        this.projectService = projectService;
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.caseMapper = caseMapper;
        this.runMapper = runMapper;
        this.retrievalService = retrievalService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RetrievalEvaluationCaseResponse createCase(
            Long projectId,
            CreateRetrievalEvaluationCaseRequest request
    ) {
        requireProject(projectId);
        String datasetVersion = request.datasetVersion().trim();
        String name = request.name().trim();
        long duplicates = caseMapper.selectCount(Wrappers.lambdaQuery(RetrievalEvaluationCase.class)
                .eq(RetrievalEvaluationCase::getProjectId, projectId)
                .eq(RetrievalEvaluationCase::getDatasetVersion, datasetVersion)
                .eq(RetrievalEvaluationCase::getName, name));
        if (duplicates > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "同一评测集中的用例名称不能重复");
        }

        LocalDateTime now = LocalDateTime.now();
        RetrievalEvaluationCase value = new RetrievalEvaluationCase();
        value.setProjectId(projectId);
        value.setDatasetVersion(datasetVersion);
        value.setName(name);
        value.setQueryText(request.query().trim());
        value.setExpectedFilePath(normalizeRelativePath(request.expectedFilePath()));
        value.setExpectedSymbolName(trimToNull(request.expectedSymbolName()));
        value.setTopK(request.topK() == null ? 5 : request.topK());
        value.setEnabled(1);
        value.setCreatedAt(now);
        value.setUpdatedAt(now);
        try {
            caseMapper.insert(value);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "同一评测集中的用例名称不能重复");
        }
        return RetrievalEvaluationCaseResponse.from(value);
    }

    @Transactional(readOnly = true)
    public List<RetrievalEvaluationCaseResponse> listCases(Long projectId, String datasetVersion) {
        requireProject(projectId);
        return loadCases(projectId, datasetVersion).stream()
                .map(RetrievalEvaluationCaseResponse::from)
                .toList();
    }

    @Transactional
    public RetrievalEvaluationRunResponse run(Long projectId, String datasetVersion) {
        ProjectResponse project = projectService.getProject(projectId);
        if (!StringUtils.hasText(project.currentRevision())) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "请先成功导入项目源码");
        }
        List<RetrievalEvaluationCase> cases = loadCases(projectId, datasetVersion);
        if (cases.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "该版本没有启用的检索评测用例");
        }

        LocalDateTime now = LocalDateTime.now();
        List<RetrievalEvaluationCaseResultResponse> results = new ArrayList<>();
        double recallSum = 0.0;
        double precisionSum = 0.0;
        double reciprocalRankSum = 0.0;
        int hitCases = 0;
        int resolvedCases = 0;

        for (RetrievalEvaluationCase evaluationCase : cases) {
            Set<Long> expectedIds = resolveExpectedChunkIds(
                    projectId,
                    project.currentRevision(),
                    evaluationCase
            );
            if (expectedIds.isEmpty()) {
                results.add(new RetrievalEvaluationCaseResultResponse(
                        evaluationCase.getName(), evaluationCase.getQueryText(), evaluationCase.getTopK(),
                        false, 0, 0, 0.0, 0.0, 0.0,
                        "预期文件或符号未在当前revision中建立索引"
                ));
                continue;
            }
            resolvedCases++;
            RetrievalSearchResponse response = retrievalService.search(projectId, new RetrievalSearchCommand(
                    evaluationCase.getQueryText(),
                    project.currentRevision(),
                    List.of(),
                    evaluationCase.getTopK(),
                    properties.getDefaultTokenBudget()
            ));
            List<Long> returnedIds = response.hits().stream().map(hit -> hit.chunkId()).toList();
            int relevantRetrieved = (int) returnedIds.stream().filter(expectedIds::contains).count();
            double recall = relevantRetrieved / (double) expectedIds.size();
            double precision = relevantRetrieved / (double) evaluationCase.getTopK();
            double reciprocalRank = reciprocalRank(returnedIds, expectedIds);
            if (relevantRetrieved > 0) {
                hitCases++;
            }
            recallSum += recall;
            precisionSum += precision;
            reciprocalRankSum += reciprocalRank;
            results.add(new RetrievalEvaluationCaseResultResponse(
                    evaluationCase.getName(), evaluationCase.getQueryText(), evaluationCase.getTopK(),
                    true, expectedIds.size(), relevantRetrieved, roundMetric(recall),
                    roundMetric(precision), roundMetric(reciprocalRank), null
            ));
        }
        if (resolvedCases == 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "评测集的预期文件或符号均未在当前revision中建立索引");
        }

        double recall = recallSum / resolvedCases;
        double precision = precisionSum / resolvedCases;
        double hitRate = hitCases / (double) resolvedCases;
        double meanReciprocalRank = reciprocalRankSum / resolvedCases;
        RetrievalEvaluationRun run = new RetrievalEvaluationRun();
        run.setProjectId(projectId);
        run.setRevision(project.currentRevision());
        run.setDatasetVersion(datasetVersion.trim());
        run.setRetrievalConfigVersion(properties.getConfigVersion());
        run.setStatus("SUCCEEDED");
        run.setTotalCases(cases.size());
        run.setResolvedCases(resolvedCases);
        run.setRecallAtK(metric(recall));
        run.setPrecisionAtK(metric(precision));
        run.setHitRateAtK(metric(hitRate));
        run.setMeanReciprocalRank(metric(meanReciprocalRank));
        run.setResultJson(writeResults(results));
        run.setCreatedAt(now);
        run.setStartedAt(now);
        run.setFinishedAt(LocalDateTime.now());
        runMapper.insert(run);
        return toResponse(run, results);
    }

    @Transactional(readOnly = true)
    public RetrievalEvaluationRunResponse latest(Long projectId, String datasetVersion) {
        requireProject(projectId);
        RetrievalEvaluationRun run = runMapper.selectOne(Wrappers.lambdaQuery(RetrievalEvaluationRun.class)
                .eq(RetrievalEvaluationRun::getProjectId, projectId)
                .eq(RetrievalEvaluationRun::getDatasetVersion, datasetVersion.trim())
                .orderByDesc(RetrievalEvaluationRun::getCreatedAt)
                .last("LIMIT 1"));
        if (run == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "该评测集暂无运行记录");
        }
        return toResponse(run, readResults(run.getResultJson()));
    }

    private List<RetrievalEvaluationCase> loadCases(Long projectId, String datasetVersion) {
        if (!StringUtils.hasText(datasetVersion)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "评测集版本不能为空");
        }
        List<RetrievalEvaluationCase> cases = caseMapper.selectList(Wrappers.lambdaQuery(RetrievalEvaluationCase.class)
                .eq(RetrievalEvaluationCase::getProjectId, projectId)
                .eq(RetrievalEvaluationCase::getDatasetVersion, datasetVersion.trim())
                .eq(RetrievalEvaluationCase::getEnabled, 1)
                .orderByAsc(RetrievalEvaluationCase::getId)
                .last("LIMIT " + (properties.getMaxEvaluationCases() + 1)));
        if (cases.size() > properties.getMaxEvaluationCases()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "单个评测集启用用例不能超过"
                    + properties.getMaxEvaluationCases() + "个");
        }
        return cases;
    }

    private Set<Long> resolveExpectedChunkIds(
            Long projectId,
            String revision,
            RetrievalEvaluationCase evaluationCase
    ) {
        KnowledgeDocument document = documentMapper.selectOne(
                Wrappers.lambdaQuery(KnowledgeDocument.class)
                        .eq(KnowledgeDocument::getProjectId, projectId)
                        .eq(KnowledgeDocument::getRevision, revision)
                        .eq(KnowledgeDocument::getFilePath, evaluationCase.getExpectedFilePath())
                        .last("LIMIT 1")
        );
        if (document == null) {
            return Set.of();
        }
        var query = Wrappers.lambdaQuery(KnowledgeChunk.class)
                .eq(KnowledgeChunk::getProjectId, projectId)
                .eq(KnowledgeChunk::getDocumentId, document.getId())
                .eq(KnowledgeChunk::getRevision, revision);
        if (StringUtils.hasText(evaluationCase.getExpectedSymbolName())) {
            query.eq(KnowledgeChunk::getSymbolName, evaluationCase.getExpectedSymbolName());
        }
        return chunkMapper.selectList(query).stream()
                .map(KnowledgeChunk::getId)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
    }

    private double reciprocalRank(List<Long> returnedIds, Set<Long> expectedIds) {
        for (int index = 0; index < returnedIds.size(); index++) {
            if (expectedIds.contains(returnedIds.get(index))) {
                return 1.0 / (index + 1.0);
            }
        }
        return 0.0;
    }

    private void requireProject(Long projectId) {
        projectService.getProject(projectId);
    }

    private String normalizeRelativePath(String path) {
        String normalizedInput = path.trim().replace('\\', '/');
        final Path normalized;
        try {
            normalized = Path.of(normalizedInput).normalize();
        } catch (InvalidPathException exception) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "预期文件路径格式不合法");
        }
        if (normalized.isAbsolute() || normalized.startsWith("..")) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "预期文件路径必须是项目内相对路径");
        }
        return normalized.toString().replace('\\', '/');
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private BigDecimal metric(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
    }

    private double roundMetric(double value) {
        return metric(value).doubleValue();
    }

    private String writeResults(List<RetrievalEvaluationCaseResultResponse> results) {
        try {
            return objectMapper.writeValueAsString(results);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("保存检索评测结果失败", exception);
        }
    }

    private List<RetrievalEvaluationCaseResultResponse> readResults(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("读取检索评测结果失败", exception);
        }
    }

    private RetrievalEvaluationRunResponse toResponse(
            RetrievalEvaluationRun run,
            List<RetrievalEvaluationCaseResultResponse> results
    ) {
        return new RetrievalEvaluationRunResponse(
                run.getId(), run.getProjectId(), run.getRevision(), run.getDatasetVersion(),
                run.getRetrievalConfigVersion(), run.getStatus(), run.getTotalCases(),
                run.getResolvedCases(), run.getRecallAtK().doubleValue(),
                run.getPrecisionAtK().doubleValue(), run.getHitRateAtK().doubleValue(),
                run.getMeanReciprocalRank().doubleValue(), run.getCreatedAt(),
                run.getFinishedAt(), results
        );
    }
}
