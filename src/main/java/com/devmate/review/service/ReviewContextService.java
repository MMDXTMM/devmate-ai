package com.devmate.review.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devmate.common.error.BusinessException;
import com.devmate.common.error.ErrorCode;
import com.devmate.knowledge.dto.RetrievalSearchResponse;
import com.devmate.knowledge.retrieval.ContextRetrievalService;
import com.devmate.knowledge.retrieval.RetrievalSearchCommand;
import com.devmate.project.service.ProjectService;
import com.devmate.review.dto.MappedSymbolResponse;
import com.devmate.review.dto.ReviewContextRequest;
import com.devmate.review.entity.CodeReviewFile;
import com.devmate.review.entity.CodeReviewTask;
import com.devmate.review.mapper.CodeReviewFileMapper;
import com.devmate.review.mapper.CodeReviewTaskMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;

@Service
public class ReviewContextService {

    private final ProjectService projectService;
    private final CodeReviewTaskMapper taskMapper;
    private final CodeReviewFileMapper fileMapper;
    private final ContextRetrievalService retrievalService;
    private final ObjectMapper objectMapper;

    public ReviewContextService(
            ProjectService projectService,
            CodeReviewTaskMapper taskMapper,
            CodeReviewFileMapper fileMapper,
            ContextRetrievalService retrievalService,
            ObjectMapper objectMapper
    ) {
        this.projectService = projectService;
        this.taskMapper = taskMapper;
        this.fileMapper = fileMapper;
        this.retrievalService = retrievalService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public RetrievalSearchResponse retrieveLatest(Long projectId, ReviewContextRequest request) {
        projectService.getProject(projectId);
        CodeReviewTask task = taskMapper.selectOne(Wrappers.lambdaQuery(CodeReviewTask.class)
                .eq(CodeReviewTask::getProjectId, projectId)
                .eq(CodeReviewTask::getStatus, "SUCCEEDED")
                .orderByDesc(CodeReviewTask::getCreatedAt)
                .last("LIMIT 1"));
        if (task == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "请先成功生成Git Diff");
        }

        List<CodeReviewFile> files = fileMapper.selectList(Wrappers.lambdaQuery(CodeReviewFile.class)
                .eq(CodeReviewFile::getReviewTaskId, task.getId())
                .orderByAsc(CodeReviewFile::getId));
        List<MappedSymbolResponse> targetSymbols = files.stream()
                .flatMap(file -> readSymbols(file.getMappedSymbolsJson()).stream())
                .filter(symbol -> "TARGET".equals(symbol.revisionSide()))
                .filter(symbol -> symbol.chunkId() != null)
                .toList();
        List<Long> seedIds = new LinkedHashSet<>(targetSymbols.stream()
                .map(MappedSymbolResponse::chunkId)
                .toList()).stream().limit(20).toList();
        if (seedIds.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "当前Diff没有可检索的目标版本Java符号");
        }
        String query = StringUtils.hasText(request.query())
                ? request.query().trim()
                : defaultQuery(targetSymbols);
        return retrievalService.search(projectId, new RetrievalSearchCommand(
                query,
                task.getTargetRevision(),
                seedIds,
                request.topK(),
                request.tokenBudget()
        ));
    }

    private List<MappedSymbolResponse> readSymbols(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("读取Diff符号失败", exception);
        }
    }

    private String defaultQuery(List<MappedSymbolResponse> symbols) {
        String query = symbols.stream()
                .map(MappedSymbolResponse::symbolName)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(10)
                .reduce((left, right) -> left + " " + right)
                .orElse("changed Java methods");
        return query.length() <= 500 ? query : query.substring(0, 500);
    }
}
