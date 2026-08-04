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

    public EmbeddingIndexStateService(
            EmbeddingIndexTaskMapper taskMapper,
            EmbeddingVectorMapper vectorMapper,
            KnowledgeChunkMapper chunkMapper,
            KnowledgeDocumentMapper documentMapper
    ) {
        this.taskMapper = taskMapper;
        this.vectorMapper = vectorMapper;
        this.chunkMapper = chunkMapper;
        this.documentMapper = documentMapper;
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
            List<String> vectorJson
    ) {
        if (chunks.size() != vectorIds.size() || chunks.size() != vectorJson.size()) {
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
        increment(context.taskId(), chunks.size(), 0);
    }

    @Transactional
    public void activateExisting(EmbeddingIndexContext context, KnowledgeChunk chunk, String vectorId) {
        chunkMapper.update(null, Wrappers.lambdaUpdate(KnowledgeChunk.class)
                .eq(KnowledgeChunk::getId, chunk.getId())
                .eq(KnowledgeChunk::getProjectId, context.projectId())
                .eq(KnowledgeChunk::getRevision, context.revision())
                .set(KnowledgeChunk::getVectorId, vectorId));
        increment(context.taskId(), 0, 1);
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
                - task.getProcessedChunks() - task.getSkippedChunks()));
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

    private void increment(Long taskId, int processed, int skipped) {
        EmbeddingIndexTask task = requireTask(taskId);
        task.setProcessedChunks(task.getProcessedChunks() + processed);
        task.setSkippedChunks(task.getSkippedChunks() + skipped);
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
