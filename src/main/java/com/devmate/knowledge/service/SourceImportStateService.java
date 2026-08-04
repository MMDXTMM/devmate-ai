package com.devmate.knowledge.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devmate.common.error.BusinessException;
import com.devmate.common.error.ErrorCode;
import com.devmate.knowledge.dto.IndexTaskResponse;
import com.devmate.knowledge.entity.IndexTask;
import com.devmate.knowledge.entity.KnowledgeDocument;
import com.devmate.knowledge.entity.KnowledgeChunk;
import com.devmate.knowledge.mapper.IndexTaskMapper;
import com.devmate.knowledge.mapper.KnowledgeChunkMapper;
import com.devmate.knowledge.mapper.KnowledgeDocumentMapper;
import com.devmate.knowledge.model.IndexTaskStatus;
import com.devmate.knowledge.source.GitRepositoryValidator;
import com.devmate.knowledge.source.ParsedSourceChunk;
import com.devmate.knowledge.source.ParsedSourceFile;
import com.devmate.project.entity.Project;
import com.devmate.project.mapper.ProjectMapper;
import com.devmate.project.model.ProjectSourceType;
import com.devmate.project.model.ProjectStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SourceImportStateService {

    private static final String TASK_TYPE_FULL = "FULL";
    private static final String DOCUMENT_STATUS_PARSED = "PARSED";

    private final ProjectMapper projectMapper;
    private final IndexTaskMapper indexTaskMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final GitRepositoryValidator repositoryValidator;
    private final ObjectMapper objectMapper;

    public SourceImportStateService(
            ProjectMapper projectMapper,
            IndexTaskMapper indexTaskMapper,
            KnowledgeDocumentMapper documentMapper,
            KnowledgeChunkMapper chunkMapper,
            GitRepositoryValidator repositoryValidator,
            ObjectMapper objectMapper
    ) {
        this.projectMapper = projectMapper;
        this.indexTaskMapper = indexTaskMapper;
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.repositoryValidator = repositoryValidator;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SourceImportContext prepare(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "项目不存在");
        }
        if (!ProjectSourceType.GIT.name().equals(project.getSourceType())) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "第一版源码导入仅支持Git项目");
        }
        if (!StringUtils.hasText(project.getSourceLocation())) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Git项目必须填写仓库地址");
        }
        repositoryValidator.validate(project.getSourceLocation());

        LocalDateTime now = LocalDateTime.now();
        int locked = projectMapper.update(null, Wrappers.lambdaUpdate(Project.class)
                .eq(Project::getId, projectId)
                .ne(Project::getStatus, ProjectStatus.INDEXING.name())
                .set(Project::getStatus, ProjectStatus.INDEXING.name())
                .set(Project::getUpdatedAt, now));
        if (locked != 1) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "项目源码正在导入，请勿重复提交");
        }

        IndexTask task = new IndexTask();
        task.setProjectId(projectId);
        task.setTaskType(TASK_TYPE_FULL);
        task.setStatus(IndexTaskStatus.RUNNING.name());
        task.setTotalFiles(0);
        task.setProcessedFiles(0);
        task.setFailedFiles(0);
        task.setCreatedAt(now);
        task.setStartedAt(now);
        indexTaskMapper.insert(task);

        String branch = StringUtils.hasText(project.getDefaultBranch())
                ? project.getDefaultBranch().trim()
                : "main";
        return new SourceImportContext(
                projectId,
                task.getId(),
                project.getSourceLocation().trim(),
                branch
        );
    }

    @Transactional
    public IndexTaskResponse complete(
            SourceImportContext context,
            String revision,
            List<ParsedSourceFile> files
    ) {
        LocalDateTime now = LocalDateTime.now();
        for (ParsedSourceFile file : files) {
            upsertDocument(context.projectId(), revision, file, now);
        }

        IndexTask task = requireTask(context.taskId());
        task.setRevision(revision);
        task.setStatus(IndexTaskStatus.SUCCEEDED.name());
        task.setTotalFiles(files.size());
        task.setProcessedFiles(files.size());
        task.setFailedFiles(0);
        task.setFinishedAt(now);
        indexTaskMapper.updateById(task);

        projectMapper.update(null, Wrappers.lambdaUpdate(Project.class)
                .eq(Project::getId, context.projectId())
                .set(Project::getStatus, ProjectStatus.READY.name())
                .set(Project::getCurrentRevision, revision)
                .set(Project::getLastIndexedAt, now)
                .set(Project::getUpdatedAt, now));
        return IndexTaskResponse.from(task);
    }

    @Transactional
    public void fail(SourceImportContext context, String errorMessage) {
        LocalDateTime now = LocalDateTime.now();
        IndexTask task = requireTask(context.taskId());
        task.setStatus(IndexTaskStatus.FAILED.name());
        task.setFailedFiles(Math.max(task.getFailedFiles(), 1));
        task.setErrorMessage(truncate(errorMessage));
        task.setFinishedAt(now);
        indexTaskMapper.updateById(task);

        projectMapper.update(null, Wrappers.lambdaUpdate(Project.class)
                .eq(Project::getId, context.projectId())
                .set(Project::getStatus, ProjectStatus.FAILED.name())
                .set(Project::getUpdatedAt, now));
    }

    @Transactional(readOnly = true)
    public IndexTaskResponse getLatest(Long projectId) {
        if (projectMapper.selectById(projectId) == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "项目不存在");
        }
        IndexTask task = indexTaskMapper.selectOne(Wrappers.lambdaQuery(IndexTask.class)
                .eq(IndexTask::getProjectId, projectId)
                .orderByDesc(IndexTask::getCreatedAt)
                .last("LIMIT 1"));
        if (task == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "项目暂无源码导入任务");
        }
        return IndexTaskResponse.from(task);
    }

    private void upsertDocument(
            Long projectId,
            String revision,
            ParsedSourceFile parsedFile,
            LocalDateTime now
    ) {
        var file = parsedFile.sourceFile();
        KnowledgeDocument document = documentMapper.selectOne(
                Wrappers.lambdaQuery(KnowledgeDocument.class)
                        .eq(KnowledgeDocument::getProjectId, projectId)
                        .eq(KnowledgeDocument::getPathHash, file.pathHash())
                        .eq(KnowledgeDocument::getRevision, revision)
                        .last("LIMIT 1")
        );
        boolean exists = document != null;
        if (!exists) {
            document = new KnowledgeDocument();
            document.setProjectId(projectId);
            document.setCreatedAt(now);
        }
        document.setSourceKind("SOURCE_CODE");
        document.setFileName(file.fileName());
        document.setFilePath(file.relativePath());
        document.setPathHash(file.pathHash());
        document.setFileType("JAVA");
        document.setContentHash(file.contentHash());
        document.setRevision(revision);
        document.setPackageName(parsedFile.packageName());
        document.setStatus(DOCUMENT_STATUS_PARSED);
        document.setChunkCount(parsedFile.chunks().size());
        document.setErrorMessage(null);
        document.setDeleted(0);
        document.setUpdatedAt(now);
        if (exists) {
            documentMapper.updateById(document);
        } else {
            documentMapper.insert(document);
        }
        replaceChunks(document, revision, parsedFile.chunks(), now);
    }

    private void replaceChunks(
            KnowledgeDocument document,
            String revision,
            List<ParsedSourceChunk> chunks,
            LocalDateTime now
    ) {
        chunkMapper.delete(Wrappers.lambdaQuery(KnowledgeChunk.class)
                .eq(KnowledgeChunk::getDocumentId, document.getId()));
        for (ParsedSourceChunk parsedChunk : chunks) {
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setProjectId(document.getProjectId());
            chunk.setDocumentId(document.getId());
            chunk.setChunkIndex(parsedChunk.chunkIndex());
            chunk.setChunkType(parsedChunk.chunkType());
            chunk.setSymbolName(parsedChunk.symbolName());
            chunk.setLanguage("JAVA");
            chunk.setContent(parsedChunk.content());
            chunk.setContentHash(parsedChunk.contentHash());
            chunk.setStartLine(parsedChunk.startLine());
            chunk.setEndLine(parsedChunk.endLine());
            chunk.setRevision(revision);
            chunk.setMetadataJson(writeMetadata(parsedChunk.annotations()));
            chunk.setCreatedAt(now);
            chunkMapper.insert(chunk);
        }
    }

    private String writeMetadata(List<String> annotations) {
        try {
            return objectMapper.writeValueAsString(new ChunkMetadata(annotations));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("序列化源码符号元数据失败", exception);
        }
    }

    private IndexTask requireTask(Long taskId) {
        IndexTask task = indexTaskMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalStateException("源码导入任务不存在");
        }
        return task;
    }

    private String truncate(String value) {
        if (!StringUtils.hasText(value)) {
            return "源码导入失败";
        }
        String trimmed = value.trim();
        return trimmed.length() <= 1000 ? trimmed : trimmed.substring(0, 1000);
    }

    private record ChunkMetadata(List<String> annotations) {
    }
}
