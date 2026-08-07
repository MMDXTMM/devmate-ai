package com.devmate.knowledge.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devmate.common.error.BusinessException;
import com.devmate.common.error.ErrorCode;
import com.devmate.knowledge.dto.EmbeddingIndexTaskResponse;
import com.devmate.knowledge.entity.EmbeddingIndexTask;
import com.devmate.knowledge.entity.EmbeddingVector;
import com.devmate.knowledge.entity.KnowledgeChunk;
import com.devmate.knowledge.entity.KnowledgeDocument;
import com.devmate.knowledge.mapper.EmbeddingIndexTaskMapper;
import com.devmate.knowledge.mapper.EmbeddingVectorMapper;
import com.devmate.knowledge.mapper.KnowledgeChunkMapper;
import com.devmate.knowledge.mapper.KnowledgeDocumentMapper;
import com.devmate.project.entity.Project;
import com.devmate.project.mapper.ProjectMapper;
import com.devmate.project.model.ProjectStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Service
public class EmbeddingIndexStateService {

    private final EmbeddingIndexTaskMapper taskMapper;
    private final EmbeddingVectorMapper vectorMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final ProjectMapper projectMapper;

    public EmbeddingIndexStateService(
            EmbeddingIndexTaskMapper taskMapper,
            EmbeddingVectorMapper vectorMapper,
            KnowledgeChunkMapper chunkMapper,
            KnowledgeDocumentMapper documentMapper,
            ProjectMapper projectMapper
    ) {
        this.taskMapper = taskMapper;
        this.vectorMapper = vectorMapper;
        this.chunkMapper = chunkMapper;
        this.documentMapper = documentMapper;
        this.projectMapper = projectMapper;
    }

    @Transactional
    public EmbeddingIndexContext start(
            Long projectId,
            String revision,
            String provider,
            String model,
            int dimensions,
            int totalChunks
    ) {
        Project project = projectMapper.selectByIdForUpdate(projectId);
        if (project == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "项目不存在");
        }
        if (ProjectStatus.INDEXING.name().equals(project.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "项目源码结构未就绪，暂不能建立向量索引");
        }
        if (!ProjectStatus.READY.name().equals(project.getStatus())
                || !revision.equals(project.getCurrentRevision())) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "请先成功导入项目源码");
        }
        long running = taskMapper.selectCount(Wrappers.lambdaQuery(EmbeddingIndexTask.class)
                .eq(EmbeddingIndexTask::getProjectId, projectId)
                .eq(EmbeddingIndexTask::getStatus, "RUNNING"));
        if (running > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该项目正在建立向量索引");
        }
        LocalDateTime now = LocalDateTime.now();
        EmbeddingIndexTask task = new EmbeddingIndexTask();
        task.setProjectId(projectId);
        task.setRevision(revision);
        task.setProvider(provider);
        task.setModelName(model);
        task.setDimensions(dimensions);
        task.setStatus("RUNNING");
        task.setTotalChunks(totalChunks);
        task.setProcessedChunks(0);
        task.setSkippedChunks(0);
        task.setReusedChunks(0);
        task.setFailedChunks(0);
        task.setCreatedAt(now);
        task.setStartedAt(now);
        taskMapper.insert(task);
        return new EmbeddingIndexContext(
                task.getId(), projectId, revision, provider, model, dimensions, totalChunks
        );
    }

    @Transactional
    public void saveBatch(
            EmbeddingIndexContext context,
            List<KnowledgeChunk> chunks,
            List<String> vectorIds,
            List<String> inputHashes,
            List<String> vectorJson
    ) {
        if (chunks.size() != vectorIds.size()
                || chunks.size() != inputHashes.size()
                || chunks.size() != vectorJson.size()) {
            throw new IllegalArgumentException("向量批次结果数量不一致");
        }
        LocalDateTime now = LocalDateTime.now();
        for (int index = 0; index < chunks.size(); index++) {
            KnowledgeChunk chunk = chunks.get(index);
            EmbeddingVector vector = vectorMapper.selectById(vectorIds.get(index));
            if (vector == null) {
                vector = new EmbeddingVector();
                vector.setVectorId(vectorIds.get(index));
                vector.setProjectId(context.projectId());
                vector.setChunkId(chunk.getId());
                vector.setRevision(context.revision());
                vector.setProvider(context.provider());
                vector.setModelName(context.model());
                vector.setDimensions(context.dimensions());
                vector.setContentHash(chunk.getContentHash());
                vector.setInputHash(inputHashes.get(index));
                vector.setVectorJson(vectorJson.get(index));
                vector.setCreatedAt(now);
                vector.setUpdatedAt(now);
                vectorMapper.insert(vector);
            }
            chunkMapper.update(null, Wrappers.lambdaUpdate(KnowledgeChunk.class)
                    .eq(KnowledgeChunk::getId, chunk.getId())
                    .eq(KnowledgeChunk::getProjectId, context.projectId())
                    .eq(KnowledgeChunk::getRevision, context.revision())
                    .set(KnowledgeChunk::getVectorId, vectorIds.get(index)));
        }
        increment(context.taskId(), chunks.size(), 0, 0);
    }

    @Transactional
    public void activateExisting(EmbeddingIndexContext context, KnowledgeChunk chunk, String vectorId) {
        chunkMapper.update(null, Wrappers.lambdaUpdate(KnowledgeChunk.class)
                .eq(KnowledgeChunk::getId, chunk.getId())
                .eq(KnowledgeChunk::getProjectId, context.projectId())
                .eq(KnowledgeChunk::getRevision, context.revision())
                .set(KnowledgeChunk::getVectorId, vectorId));
        increment(context.taskId(), 0, 1, 0);
    }

    @Transactional
    public void reuse(
            EmbeddingIndexContext context,
            KnowledgeChunk chunk,
            String vectorId,
            String inputHash,
            String vectorJson
    ) {
        LocalDateTime now = LocalDateTime.now();
        EmbeddingVector vector = new EmbeddingVector();
        vector.setVectorId(vectorId);
        vector.setProjectId(context.projectId());
        vector.setChunkId(chunk.getId());
        vector.setRevision(context.revision());
        vector.setProvider(context.provider());
        vector.setModelName(context.model());
        vector.setDimensions(context.dimensions());
        vector.setContentHash(chunk.getContentHash());
        vector.setInputHash(inputHash);
        vector.setVectorJson(vectorJson);
        vector.setCreatedAt(now);
        vector.setUpdatedAt(now);
        vectorMapper.insert(vector);
        chunkMapper.update(null, Wrappers.lambdaUpdate(KnowledgeChunk.class)
                .eq(KnowledgeChunk::getId, chunk.getId())
                .eq(KnowledgeChunk::getProjectId, context.projectId())
                .eq(KnowledgeChunk::getRevision, context.revision())
                .set(KnowledgeChunk::getVectorId, vectorId));
        increment(context.taskId(), 0, 0, 1);
    }

    @Transactional
    public EmbeddingIndexTaskResponse complete(
            EmbeddingIndexContext context,
            Collection<Long> documentIds
    ) {
        EmbeddingIndexTask task = requireTask(context.taskId());
        if (!documentIds.isEmpty()) {
            documentMapper.update(null, Wrappers.lambdaUpdate(KnowledgeDocument.class)
                    .in(KnowledgeDocument::getId, documentIds)
                    .eq(KnowledgeDocument::getProjectId, context.projectId())
                    .eq(KnowledgeDocument::getRevision, context.revision())
                    .set(KnowledgeDocument::getStatus, "INDEXED")
                    .set(KnowledgeDocument::getUpdatedAt, LocalDateTime.now()));
        }
        task.setStatus("SUCCEEDED");
        task.setFinishedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        return EmbeddingIndexTaskResponse.from(task);
    }

    @Transactional
    public void fail(EmbeddingIndexContext context, String message) {
        EmbeddingIndexTask task = requireTask(context.taskId());
        task.setStatus("FAILED");
        task.setFailedChunks(Math.max(1, context.totalChunks()
                - task.getProcessedChunks() - task.getSkippedChunks() - task.getReusedChunks()));
        task.setErrorMessage(truncate(message));
        task.setFinishedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    @Transactional(readOnly = true)
    public EmbeddingIndexTaskResponse latest(Long projectId) {
        EmbeddingIndexTask task = taskMapper.selectOne(Wrappers.lambdaQuery(EmbeddingIndexTask.class)
                .eq(EmbeddingIndexTask::getProjectId, projectId)
                .orderByDesc(EmbeddingIndexTask::getCreatedAt)
                .last("LIMIT 1"));
        if (task == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "项目暂无向量索引任务");
        }
        return EmbeddingIndexTaskResponse.from(task);
    }

    private void increment(Long taskId, int processed, int skipped, int reused) {
        EmbeddingIndexTask task = requireTask(taskId);
        task.setProcessedChunks(task.getProcessedChunks() + processed);
        task.setSkippedChunks(task.getSkippedChunks() + skipped);
        task.setReusedChunks(task.getReusedChunks() + reused);
        taskMapper.updateById(task);
    }

    private EmbeddingIndexTask requireTask(Long taskId) {
        EmbeddingIndexTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalStateException("向量索引任务不存在");
        }
        return task;
    }

    private String truncate(String value) {
        if (value == null) {
            return "向量索引失败";
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
