package com.devmate.review.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devmate.common.error.BusinessException;
import com.devmate.common.error.ErrorCode;
import com.devmate.knowledge.entity.IndexTask;
import com.devmate.knowledge.mapper.IndexTaskMapper;
import com.devmate.project.entity.Project;
import com.devmate.project.mapper.ProjectMapper;
import com.devmate.review.dto.MappedSymbolResponse;
import com.devmate.review.dto.ReviewDiffResponse;
import com.devmate.review.dto.ReviewFileResponse;
import com.devmate.review.entity.CodeReviewFile;
import com.devmate.review.entity.CodeReviewTask;
import com.devmate.review.mapper.CodeReviewFileMapper;
import com.devmate.review.mapper.CodeReviewTaskMapper;
import com.devmate.review.model.GitDiffResult;
import com.devmate.review.model.LineRange;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReviewDiffStateService {

    private final ProjectMapper projectMapper;
    private final IndexTaskMapper indexTaskMapper;
    private final CodeReviewTaskMapper taskMapper;
    private final CodeReviewFileMapper fileMapper;
    private final ObjectMapper objectMapper;

    public ReviewDiffStateService(
            ProjectMapper projectMapper,
            IndexTaskMapper indexTaskMapper,
            CodeReviewTaskMapper taskMapper,
            CodeReviewFileMapper fileMapper,
            ObjectMapper objectMapper
    ) {
        this.projectMapper = projectMapper;
        this.indexTaskMapper = indexTaskMapper;
        this.taskMapper = taskMapper;
        this.fileMapper = fileMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ReviewDiffContext prepare(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "项目不存在");
        }
        IndexTask indexTask = indexTaskMapper.selectOne(Wrappers.lambdaQuery(IndexTask.class)
                .eq(IndexTask::getProjectId, projectId)
                .eq(IndexTask::getStatus, "SUCCEEDED")
                .orderByDesc(IndexTask::getCreatedAt)
                .last("LIMIT 1"));
        if (indexTask == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "请先成功导入项目源码");
        }
        LocalDateTime now = LocalDateTime.now();
        CodeReviewTask task = new CodeReviewTask();
        task.setProjectId(projectId);
        task.setIndexTaskId(indexTask.getId());
        task.setTriggerType("MANUAL");
        task.setStatus("RUNNING");
        task.setChangedFiles(0);
        task.setFullyMappedFiles(0);
        task.setPartiallyMappedFiles(0);
        task.setSkippedFiles(0);
        task.setCreatedAt(now);
        task.setStartedAt(now);
        taskMapper.insert(task);
        return new ReviewDiffContext(projectId, task.getId(), indexTask.getId());
    }

    @Transactional
    public ReviewDiffResponse complete(
            ReviewDiffContext context,
            GitDiffResult diff,
            List<MappedReviewFile> files
    ) {
        LocalDateTime now = LocalDateTime.now();
        for (MappedReviewFile mapped : files) {
            CodeReviewFile file = new CodeReviewFile();
            file.setReviewTaskId(context.reviewTaskId());
            file.setProjectId(context.projectId());
            file.setOldPath(mapped.changedFile().oldPath());
            file.setNewPath(mapped.changedFile().newPath());
            file.setChangeType(mapped.changedFile().changeType());
            file.setCoverageStatus(mapped.coverageStatus());
            file.setAdditions(mapped.changedFile().additions());
            file.setDeletions(mapped.changedFile().deletions());
            file.setChangedLinesJson(writeJson(mapped.changedFile().targetLineRanges()));
            file.setMappedSymbolsJson(writeJson(mapped.mappedSymbols()));
            file.setSkipReason(mapped.skipReason());
            file.setCreatedAt(now);
            fileMapper.insert(file);
        }

        CodeReviewTask task = requireTask(context.reviewTaskId());
        task.setBaseRevision(diff.baseRevision());
        task.setTargetRevision(diff.targetRevision());
        task.setStatus("SUCCEEDED");
        task.setChangedFiles(files.size());
        task.setFullyMappedFiles(count(files, "FULL"));
        task.setPartiallyMappedFiles(count(files, "PARTIAL"));
        task.setSkippedFiles(count(files, "SKIPPED"));
        task.setFinishedAt(now);
        taskMapper.updateById(task);
        return toResponse(task, listFiles(task.getId()));
    }

    @Transactional
    public void fail(ReviewDiffContext context, String errorMessage) {
        CodeReviewTask task = requireTask(context.reviewTaskId());
        task.setStatus("FAILED");
        task.setErrorMessage(truncate(errorMessage));
        task.setFinishedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    @Transactional(readOnly = true)
    public ReviewDiffResponse getLatest(Long projectId) {
        if (projectMapper.selectById(projectId) == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "项目不存在");
        }
        CodeReviewTask task = taskMapper.selectOne(Wrappers.lambdaQuery(CodeReviewTask.class)
                .eq(CodeReviewTask::getProjectId, projectId)
                .orderByDesc(CodeReviewTask::getCreatedAt)
                .last("LIMIT 1"));
        if (task == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "项目暂无Diff任务");
        }
        return toResponse(task, listFiles(task.getId()));
    }

    private List<CodeReviewFile> listFiles(Long reviewTaskId) {
        return fileMapper.selectList(Wrappers.lambdaQuery(CodeReviewFile.class)
                .eq(CodeReviewFile::getReviewTaskId, reviewTaskId)
                .orderByAsc(CodeReviewFile::getId));
    }

    private ReviewDiffResponse toResponse(CodeReviewTask task, List<CodeReviewFile> files) {
        return new ReviewDiffResponse(
                task.getId(), task.getProjectId(), task.getBaseRevision(), task.getTargetRevision(),
                task.getStatus(), task.getChangedFiles(), task.getFullyMappedFiles(),
                task.getPartiallyMappedFiles(), task.getSkippedFiles(), task.getErrorMessage(),
                task.getCreatedAt(), task.getFinishedAt(), files.stream().map(this::toFileResponse).toList()
        );
    }

    private ReviewFileResponse toFileResponse(CodeReviewFile file) {
        return new ReviewFileResponse(
                file.getId(), file.getOldPath(), file.getNewPath(), file.getChangeType(),
                file.getCoverageStatus(), file.getAdditions(), file.getDeletions(),
                readJson(file.getChangedLinesJson(), new TypeReference<>() {}),
                readJson(file.getMappedSymbolsJson(), new TypeReference<>() {}),
                file.getSkipReason()
        );
    }

    private int count(List<MappedReviewFile> files, String status) {
        return (int) files.stream().filter(file -> status.equals(file.coverageStatus())).count();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("序列化Diff覆盖信息失败", exception);
        }
    }

    private <T> T readJson(String value, TypeReference<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("读取Diff覆盖信息失败", exception);
        }
    }

    private CodeReviewTask requireTask(Long taskId) {
        CodeReviewTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalStateException("Diff任务不存在");
        }
        return task;
    }

    private String truncate(String value) {
        if (!StringUtils.hasText(value)) {
            return "Git Diff执行失败";
        }
        String trimmed = value.trim();
        return trimmed.length() <= 1000 ? trimmed : trimmed.substring(0, 1000);
    }
}
