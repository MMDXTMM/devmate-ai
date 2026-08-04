package com.devmate.review.service;

import com.devmate.common.error.BusinessException;
import com.devmate.common.error.ErrorCode;
import com.devmate.knowledge.source.SourceImportException;
import com.devmate.knowledge.source.WorkspaceManager;
import com.devmate.review.dto.CreateReviewDiffRequest;
import com.devmate.review.dto.ReviewDiffResponse;
import com.devmate.review.model.GitDiffResult;
import com.devmate.review.source.GitDiffAnalyzer;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

@Service
public class ReviewDiffService {

    private final ReviewDiffStateService stateService;
    private final WorkspaceManager workspaceManager;
    private final GitDiffAnalyzer diffAnalyzer;
    private final DiffSymbolMapper symbolMapper;

    public ReviewDiffService(
            ReviewDiffStateService stateService,
            WorkspaceManager workspaceManager,
            GitDiffAnalyzer diffAnalyzer,
            DiffSymbolMapper symbolMapper
    ) {
        this.stateService = stateService;
        this.workspaceManager = workspaceManager;
        this.diffAnalyzer = diffAnalyzer;
        this.symbolMapper = symbolMapper;
    }

    public ReviewDiffResponse create(Long projectId, CreateReviewDiffRequest request) {
        ReviewDiffContext context = stateService.prepare(projectId);
        try {
            Path repositoryRoot = workspaceManager.requireTaskDirectory(
                    projectId,
                    context.indexTaskId()
            );
            GitDiffResult diff = diffAnalyzer.analyze(
                    repositoryRoot,
                    request.baseRevision(),
                    request.targetRevision()
            );
            List<MappedReviewFile> mappedFiles = diff.files().stream()
                    .map(file -> symbolMapper.map(projectId, diff.targetRevision(), file))
                    .toList();
            return stateService.complete(context, diff, mappedFiles);
        } catch (RuntimeException exception) {
            String message = exception.getMessage() == null ? "Git Diff执行失败" : exception.getMessage();
            stateService.fail(context, message);
            if (exception instanceof BusinessException businessException) {
                throw businessException;
            }
            if (exception instanceof SourceImportException) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, message);
            }
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, message);
        }
    }

    public ReviewDiffResponse getLatest(Long projectId) {
        return stateService.getLatest(projectId);
    }
}
