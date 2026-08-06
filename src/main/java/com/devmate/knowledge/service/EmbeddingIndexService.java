package com.devmate.knowledge.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devmate.common.error.BusinessException;
import com.devmate.common.error.ErrorCode;
import com.devmate.knowledge.config.EmbeddingProperties;
import com.devmate.knowledge.dto.EmbeddingIndexTaskResponse;
import com.devmate.knowledge.embedding.EmbeddingBatch;
import com.devmate.knowledge.embedding.EmbeddingProvider;
import com.devmate.knowledge.embedding.EmbeddingProviderRegistry;
import com.devmate.knowledge.entity.EmbeddingVector;
import com.devmate.knowledge.entity.EmbeddingIndexTask;
import com.devmate.knowledge.entity.KnowledgeChunk;
import com.devmate.knowledge.entity.KnowledgeDocument;
import com.devmate.knowledge.mapper.EmbeddingIndexTaskMapper;
import com.devmate.knowledge.mapper.EmbeddingVectorMapper;
import com.devmate.knowledge.mapper.KnowledgeChunkMapper;
import com.devmate.knowledge.mapper.KnowledgeDocumentMapper;
import com.devmate.knowledge.vector.MySqlVectorStore;
import com.devmate.project.dto.ProjectResponse;
import com.devmate.project.service.ProjectService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class EmbeddingIndexService {

    private final ProjectService projectService;
    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final EmbeddingIndexTaskMapper indexTaskMapper;
    private final EmbeddingVectorMapper vectorMapper;
    private final EmbeddingProviderRegistry providerRegistry;
    private final EmbeddingIndexStateService stateService;
    private final MySqlVectorStore vectorStore;
    private final EmbeddingProperties properties;

    public EmbeddingIndexService(
            ProjectService projectService,
            KnowledgeChunkMapper chunkMapper,
            KnowledgeDocumentMapper documentMapper,
            EmbeddingIndexTaskMapper indexTaskMapper,
            EmbeddingVectorMapper vectorMapper,
            EmbeddingProviderRegistry providerRegistry,
            EmbeddingIndexStateService stateService,
            MySqlVectorStore vectorStore,
            EmbeddingProperties properties
    ) {
        this.projectService = projectService;
        this.chunkMapper = chunkMapper;
        this.documentMapper = documentMapper;
        this.indexTaskMapper = indexTaskMapper;
        this.vectorMapper = vectorMapper;
        this.providerRegistry = providerRegistry;
        this.stateService = stateService;
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    public EmbeddingIndexTaskResponse index(Long projectId) {
        validateLimits();
        ProjectResponse project = projectService.getProject(projectId);
        if (!StringUtils.hasText(project.currentRevision())) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "请先成功导入项目源码");
        }
        List<KnowledgeChunk> loaded = chunkMapper.selectList(Wrappers.lambdaQuery(KnowledgeChunk.class)
                .eq(KnowledgeChunk::getProjectId, projectId)
                .eq(KnowledgeChunk::getRevision, project.currentRevision())
                .orderByAsc(KnowledgeChunk::getId)
                .last("LIMIT " + (properties.getMaxIndexChunks() + 1)));
        if (loaded.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "当前项目版本没有可向量化的知识Chunk");
        }
        if (loaded.size() > properties.getMaxIndexChunks()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "项目Chunk数量超过同步向量化上限");
        }
        EmbeddingProvider provider = providerRegistry.current();
        EmbeddingIndexContext context = stateService.start(
                projectId, project.currentRevision(), provider.providerName(), provider.modelName(),
                provider.dimensions(), loaded.size()
        );
        try {
            Map<Long, KnowledgeDocument> documents = loadDocuments(loaded);
            List<PendingEmbedding> pending = new ArrayList<>();
            for (KnowledgeChunk chunk : loaded) {
                String vectorId = vectorId(context, chunk);
                EmbeddingVector existing = vectorMapper.selectById(vectorId);
                if (existing != null) {
                    stateService.activateExisting(context, chunk, vectorId);
                } else {
                    String text = embeddingText(chunk, documents.get(chunk.getDocumentId()));
                    pending.add(new PendingEmbedding(chunk, vectorId, inputHash(text), text));
                }
            }
            Map<String, EmbeddingVector> reusable = loadReusableVectors(context, pending);
            List<PendingEmbedding> requiresEmbedding = new ArrayList<>();
            for (PendingEmbedding candidate : pending) {
                EmbeddingVector source = reusable.get(candidate.inputHash());
                if (source == null) {
                    requiresEmbedding.add(candidate);
                } else {
                    stateService.reuse(
                            context,
                            candidate.chunk(),
                            candidate.vectorId(),
                            candidate.inputHash(),
                            source.getVectorJson()
                    );
                }
            }
            for (int offset = 0; offset < requiresEmbedding.size(); offset += properties.getBatchSize()) {
                List<PendingEmbedding> batch = requiresEmbedding.subList(
                        offset,
                        Math.min(offset + properties.getBatchSize(), requiresEmbedding.size())
                );
                List<String> texts = batch.stream().map(PendingEmbedding::text).toList();
                EmbeddingBatch embedded = provider.embed(texts);
                if (embedded.vectors().size() != batch.size()) {
                    throw new IllegalStateException("Embedding返回数量与请求不一致");
                }
                List<KnowledgeChunk> batchChunks = batch.stream().map(PendingEmbedding::chunk).toList();
                List<String> ids = batch.stream().map(PendingEmbedding::vectorId).toList();
                List<String> inputHashes = batch.stream().map(PendingEmbedding::inputHash).toList();
                List<String> json = embedded.vectors().stream().map(vectorStore::writeVector).toList();
                stateService.saveBatch(context, batchChunks, ids, inputHashes, json);
            }
            return stateService.complete(context, documents.keySet());
        } catch (RuntimeException exception) {
            stateService.fail(context, safeMessage(exception));
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, safeMessage(exception));
        }
    }

    public EmbeddingIndexTaskResponse latest(Long projectId) {
        projectService.getProject(projectId);
        return stateService.latest(projectId);
    }

    private Map<Long, KnowledgeDocument> loadDocuments(List<KnowledgeChunk> chunks) {
        return documentMapper.selectBatchIds(chunks.stream()
                        .map(KnowledgeChunk::getDocumentId)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(KnowledgeDocument::getId, Function.identity()));
    }

    private String embeddingText(KnowledgeChunk chunk, KnowledgeDocument document) {
        String value = "file: " + (document == null ? "" : document.getFilePath())
                + "\ntype: " + value(chunk.getChunkType())
                + "\nsymbol: " + value(chunk.getSymbolName())
                + "\ncontent:\n" + value(chunk.getContent());
        int max = properties.getMaxInputCharacters();
        return value.length() <= max ? value : value.substring(0, max);
    }

    private Map<String, EmbeddingVector> loadReusableVectors(
            EmbeddingIndexContext context,
            List<PendingEmbedding> pending
    ) {
        if (pending.isEmpty()) {
            return Map.of();
        }
        EmbeddingIndexTask previousTask = indexTaskMapper.selectOne(
                Wrappers.lambdaQuery(EmbeddingIndexTask.class)
                        .eq(EmbeddingIndexTask::getProjectId, context.projectId())
                        .eq(EmbeddingIndexTask::getProvider, context.provider())
                        .eq(EmbeddingIndexTask::getModelName, context.model())
                        .eq(EmbeddingIndexTask::getDimensions, context.dimensions())
                        .eq(EmbeddingIndexTask::getStatus, "SUCCEEDED")
                        .ne(EmbeddingIndexTask::getRevision, context.revision())
                        .orderByDesc(EmbeddingIndexTask::getCreatedAt)
                        .orderByDesc(EmbeddingIndexTask::getId)
                        .last("LIMIT 1")
        );
        if (previousTask == null) {
            return Map.of();
        }
        Set<String> inputHashes = pending.stream()
                .map(PendingEmbedding::inputHash)
                .collect(Collectors.toSet());
        Map<String, EmbeddingVector> reusable = new java.util.HashMap<>();
        List<String> hashes = new ArrayList<>(inputHashes);
        int queryBatchSize = 500;
        for (int offset = 0; offset < hashes.size(); offset += queryBatchSize) {
            List<String> batch = hashes.subList(offset, Math.min(offset + queryBatchSize, hashes.size()));
            vectorMapper.selectList(Wrappers.lambdaQuery(EmbeddingVector.class)
                            .eq(EmbeddingVector::getProjectId, context.projectId())
                            .eq(EmbeddingVector::getRevision, previousTask.getRevision())
                            .eq(EmbeddingVector::getProvider, context.provider())
                            .eq(EmbeddingVector::getModelName, context.model())
                            .eq(EmbeddingVector::getDimensions, context.dimensions())
                            .in(EmbeddingVector::getInputHash, batch))
                    .forEach(vector -> reusable.putIfAbsent(vector.getInputHash(), vector));
        }
        return reusable;
    }

    private String inputHash(String text) {
        return sha256(text);
    }

    private String vectorId(EmbeddingIndexContext context, KnowledgeChunk chunk) {
        String input = context.projectId() + "|" + chunk.getId() + "|" + context.provider()
                + "|" + context.model() + "|" + context.dimensions() + "|" + chunk.getContentHash();
        return "vec_" + sha256(input);
    }

    private String sha256(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持SHA-256", exception);
        }
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return StringUtils.hasText(message) ? message : "向量索引失败";
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private void validateLimits() {
        if (properties.getBatchSize() < 1 || properties.getBatchSize() > 10
                || properties.getMaxInputCharacters() < 100
                || properties.getMaxIndexChunks() < 1) {
            throw new IllegalStateException("Embedding索引配置不合法");
        }
    }

    private record PendingEmbedding(
            KnowledgeChunk chunk,
            String vectorId,
            String inputHash,
            String text
    ) {
    }
}
