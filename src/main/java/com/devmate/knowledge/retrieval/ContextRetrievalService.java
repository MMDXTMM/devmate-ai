package com.devmate.knowledge.retrieval;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devmate.common.error.BusinessException;
import com.devmate.common.error.ErrorCode;
import com.devmate.knowledge.config.RetrievalProperties;
import com.devmate.knowledge.dto.RetrievalHitResponse;
import com.devmate.knowledge.dto.RetrievalSearchResponse;
import com.devmate.knowledge.dto.RetrievalTrimmedResponse;
import com.devmate.knowledge.entity.CodeReference;
import com.devmate.knowledge.entity.KnowledgeChunk;
import com.devmate.knowledge.entity.KnowledgeDocument;
import com.devmate.knowledge.mapper.CodeReferenceMapper;
import com.devmate.knowledge.mapper.KnowledgeChunkMapper;
import com.devmate.knowledge.mapper.KnowledgeDocumentMapper;
import com.devmate.knowledge.embedding.EmbeddingProvider;
import com.devmate.knowledge.vector.VectorMatch;
import com.devmate.knowledge.vector.VectorRetrievalService;
import com.devmate.knowledge.vector.VectorSearchResult;
import com.devmate.project.dto.ProjectResponse;
import com.devmate.project.service.ProjectService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ContextRetrievalService {

    private static final String TRIM_DUPLICATE = "DUPLICATE_CONTENT";
    private static final String TRIM_TOKEN_BUDGET = "TOKEN_BUDGET";
    private static final String TRIM_TOP_K = "TOP_K";

    private final ProjectService projectService;
    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final CodeReferenceMapper referenceMapper;
    private final LexicalGraphRanker ranker;
    private final HybridRetrievalRanker hybridRanker;
    private final VectorRetrievalService vectorRetrievalService;
    private final RetrievalProperties properties;

    public ContextRetrievalService(
            ProjectService projectService,
            KnowledgeChunkMapper chunkMapper,
            KnowledgeDocumentMapper documentMapper,
            CodeReferenceMapper referenceMapper,
            LexicalGraphRanker ranker,
            HybridRetrievalRanker hybridRanker,
            VectorRetrievalService vectorRetrievalService,
            RetrievalProperties properties
    ) {
        this.projectService = projectService;
        this.chunkMapper = chunkMapper;
        this.documentMapper = documentMapper;
        this.referenceMapper = referenceMapper;
        this.ranker = ranker;
        this.hybridRanker = hybridRanker;
        this.vectorRetrievalService = vectorRetrievalService;
        this.properties = properties;
    }

    public RetrievalSearchResponse search(Long projectId, RetrievalSearchCommand command) {
        ProjectResponse project = projectService.getProject(projectId);
        String revision = resolveRevision(project, command.revision());
        int topK = command.topK() == null ? properties.getDefaultTopK() : command.topK();
        int tokenBudget = command.tokenBudget() == null
                ? properties.getDefaultTokenBudget()
                : command.tokenBudget();
        if (topK < 1 || topK > 20 || tokenBudget < 100 || tokenBudget > 12000) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "检索数量或Token预算超出允许范围");
        }

        List<KnowledgeChunk> loaded = chunkMapper.selectList(
                Wrappers.lambdaQuery(KnowledgeChunk.class)
                        .eq(KnowledgeChunk::getProjectId, projectId)
                        .eq(KnowledgeChunk::getRevision, revision)
                        .orderByAsc(KnowledgeChunk::getId)
                        .last("LIMIT " + (properties.getCandidateLimit() + 1))
        );
        if (loaded.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "该项目版本尚未建立知识索引");
        }
        boolean candidateLimitReached = loaded.size() > properties.getCandidateLimit();
        List<KnowledgeChunk> candidates = new ArrayList<>(loaded.stream()
                .limit(properties.getCandidateLimit())
                .toList());

        Set<Long> seedIds = validateAndLoadSeeds(
                projectId,
                revision,
                command.seedChunkIds(),
                candidates
        );
        ReferenceLoad referenceLoad = loadSeedReferences(projectId, revision, seedIds);
        Map<Long, Set<String>> graphReasons = graphReasons(seedIds, referenceLoad.references());
        includeGraphNeighbors(projectId, revision, graphReasons.keySet(), candidates);

        RetrievalMode requestedMode = command.retrievalMode() == null
                ? RetrievalMode.HYBRID
                : command.retrievalMode();
        EmbeddingProvider embeddingProvider = vectorRetrievalService.currentProvider();
        VectorSearchResult vectorResult = requestedMode == RetrievalMode.LEXICAL
                ? VectorSearchResult.unavailable(null)
                : vectorRetrievalService.search(
                        projectId,
                        revision,
                        command.query().trim(),
                        properties.getCandidateLimit()
                );
        includeVectorMatches(projectId, revision, vectorResult.matches(), candidates);
        Map<Long, KnowledgeDocument> documents = loadDocuments(candidates);
        List<RetrievalCandidate> lexicalRanked = ranker.rank(
                candidates,
                documents,
                command.query().trim(),
                seedIds,
                graphReasons
        );
        RetrievalMode effectiveMode = requestedMode == RetrievalMode.LEXICAL || !vectorResult.available()
                ? RetrievalMode.LEXICAL
                : requestedMode;
        Map<Long, KnowledgeChunk> chunksById = candidates.stream()
                .collect(Collectors.toMap(KnowledgeChunk::getId, Function.identity()));
        List<RetrievalCandidate> ranked = hybridRanker.fuse(
                effectiveMode,
                lexicalRanked,
                vectorResult.matches(),
                chunksById,
                documents
        );
        BudgetedResults budgeted = applyBudget(ranked, topK, tokenBudget);

        List<RetrievalTrimmedResponse> trimmedDetails = budgeted.trimmed().stream()
                .limit(properties.getMaxTrimmedDetails())
                .map(this::toTrimmedResponse)
                .toList();
        int omittedTrimmedDetails = Math.max(0, budgeted.trimmed().size() - trimmedDetails.size());
        return new RetrievalSearchResponse(
                projectId,
                revision,
                command.query().trim(),
                configVersion(effectiveMode, embeddingProvider),
                requestedMode.name(),
                requestedMode == effectiveMode ? effectiveMode.name() : "LEXICAL_FALLBACK",
                embeddingProvider.providerName(),
                embeddingProvider.modelName(),
                vectorResult.available(),
                vectorResult.matches().size(),
                vectorResult.limitReached(),
                vectorResult.degradationReason(),
                candidates.size(),
                candidateLimitReached,
                referenceLoad.limitReached(),
                topK,
                tokenBudget,
                budgeted.usedTokens(),
                budgeted.selected().size(),
                budgeted.trimmed().size(),
                omittedTrimmedDetails,
                budgeted.selected().stream().map(this::toHitResponse).toList(),
                trimmedDetails
        );
    }

    public String configVersion(RetrievalMode mode) {
        return configVersion(mode, vectorRetrievalService.currentProvider());
    }

    private String configVersion(RetrievalMode mode, EmbeddingProvider provider) {
        if (mode == RetrievalMode.LEXICAL) {
            return properties.getConfigVersion();
        }
        return properties.getConfigVersion() + "+" + provider.providerName().toLowerCase()
                + ":" + provider.modelName() + ":" + provider.dimensions();
    }

    private String resolveRevision(ProjectResponse project, String requestedRevision) {
        String revision = StringUtils.hasText(requestedRevision)
                ? requestedRevision.trim().toLowerCase()
                : project.currentRevision();
        if (!StringUtils.hasText(revision)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "请先成功导入项目源码");
        }
        return revision;
    }

    private Set<Long> validateAndLoadSeeds(
            Long projectId,
            String revision,
            List<Long> requestedSeedIds,
            List<KnowledgeChunk> candidates
    ) {
        Set<Long> seedIds = requestedSeedIds == null
                ? Set.of()
                : new LinkedHashSet<>(requestedSeedIds);
        if (seedIds.isEmpty()) {
            return Set.of();
        }
        List<KnowledgeChunk> seeds = chunkMapper.selectBatchIds(seedIds);
        boolean allValid = seeds.size() == seedIds.size() && seeds.stream().allMatch(chunk ->
                Objects.equals(projectId, chunk.getProjectId()) && revision.equals(chunk.getRevision())
        );
        if (!allValid) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "种子Chunk不属于当前项目版本");
        }
        appendMissing(candidates, seeds);
        return Set.copyOf(seedIds);
    }

    private ReferenceLoad loadSeedReferences(Long projectId, String revision, Set<Long> seedIds) {
        if (seedIds.isEmpty()) {
            return new ReferenceLoad(List.of(), false);
        }
        List<CodeReference> loaded = referenceMapper.selectList(Wrappers.lambdaQuery(CodeReference.class)
                .eq(CodeReference::getProjectId, projectId)
                .eq(CodeReference::getRevision, revision)
                .and(wrapper -> wrapper.in(CodeReference::getSourceChunkId, seedIds)
                        .or()
                        .in(CodeReference::getTargetChunkId, seedIds))
                .orderByAsc(CodeReference::getId)
                .last("LIMIT " + (properties.getReferenceLimit() + 1)));
        boolean limitReached = loaded.size() > properties.getReferenceLimit();
        return new ReferenceLoad(
                loaded.stream().limit(properties.getReferenceLimit()).toList(),
                limitReached
        );
    }

    private Map<Long, Set<String>> graphReasons(Set<Long> seedIds, List<CodeReference> references) {
        Map<Long, Set<String>> reasons = new HashMap<>();
        for (CodeReference reference : references) {
            if (seedIds.contains(reference.getSourceChunkId()) && reference.getTargetChunkId() != null) {
                reasons.computeIfAbsent(reference.getTargetChunkId(), ignored -> new LinkedHashSet<>())
                        .add("OUTGOING_" + reference.getReferenceKind());
            }
            if (reference.getTargetChunkId() != null && seedIds.contains(reference.getTargetChunkId())) {
                reasons.computeIfAbsent(reference.getSourceChunkId(), ignored -> new LinkedHashSet<>())
                        .add("INCOMING_" + reference.getReferenceKind());
            }
        }
        return reasons;
    }

    private void includeGraphNeighbors(
            Long projectId,
            String revision,
            Collection<Long> neighborIds,
            List<KnowledgeChunk> candidates
    ) {
        if (neighborIds.isEmpty()) {
            return;
        }
        List<KnowledgeChunk> neighbors = chunkMapper.selectBatchIds(neighborIds).stream()
                .filter(chunk -> Objects.equals(projectId, chunk.getProjectId()))
                .filter(chunk -> revision.equals(chunk.getRevision()))
                .toList();
        appendMissing(candidates, neighbors);
    }

    private void includeVectorMatches(
            Long projectId,
            String revision,
            List<VectorMatch> matches,
            List<KnowledgeChunk> candidates
    ) {
        if (matches.isEmpty()) {
            return;
        }
        List<KnowledgeChunk> vectorChunks = chunkMapper.selectBatchIds(
                        matches.stream().map(VectorMatch::chunkId).toList()
                ).stream()
                .filter(chunk -> Objects.equals(projectId, chunk.getProjectId()))
                .filter(chunk -> revision.equals(chunk.getRevision()))
                .toList();
        appendMissing(candidates, vectorChunks);
    }

    private void appendMissing(List<KnowledgeChunk> target, List<KnowledgeChunk> additional) {
        Set<Long> present = target.stream().map(KnowledgeChunk::getId).collect(Collectors.toSet());
        additional.stream()
                .filter(chunk -> present.add(chunk.getId()))
                .forEach(target::add);
    }

    private Map<Long, KnowledgeDocument> loadDocuments(List<KnowledgeChunk> chunks) {
        Set<Long> documentIds = chunks.stream()
                .map(KnowledgeChunk::getDocumentId)
                .collect(Collectors.toSet());
        return documentMapper.selectBatchIds(documentIds).stream()
                .collect(Collectors.toMap(KnowledgeDocument::getId, Function.identity()));
    }

    private BudgetedResults applyBudget(List<RetrievalCandidate> ranked, int topK, int tokenBudget) {
        List<RetrievalCandidate> deduplicated = new ArrayList<>();
        List<TrimmedCandidate> trimmed = new ArrayList<>();
        Map<String, RetrievalCandidate> byContent = new LinkedHashMap<>();
        for (RetrievalCandidate candidate : ranked) {
            String key = StringUtils.hasText(candidate.chunk().getContentHash())
                    ? candidate.chunk().getContentHash()
                    : "chunk:" + candidate.chunk().getId();
            if (byContent.putIfAbsent(key, candidate) == null) {
                deduplicated.add(candidate);
            } else {
                trimmed.add(new TrimmedCandidate(candidate, TRIM_DUPLICATE));
            }
        }

        List<RetrievalCandidate> selected = new ArrayList<>();
        int usedTokens = 0;
        for (RetrievalCandidate candidate : deduplicated) {
            if (selected.size() >= topK) {
                trimmed.add(new TrimmedCandidate(candidate, TRIM_TOP_K));
                continue;
            }
            if (usedTokens + candidate.estimatedTokens() > tokenBudget) {
                trimmed.add(new TrimmedCandidate(candidate, TRIM_TOKEN_BUDGET));
                continue;
            }
            selected.add(candidate);
            usedTokens += candidate.estimatedTokens();
        }
        trimmed.sort(Comparator.comparingDouble(
                (TrimmedCandidate value) -> value.candidate().score()
        ).reversed());
        return new BudgetedResults(List.copyOf(selected), List.copyOf(trimmed), usedTokens);
    }

    private RetrievalHitResponse toHitResponse(RetrievalCandidate candidate) {
        KnowledgeChunk chunk = candidate.chunk();
        KnowledgeDocument document = candidate.document();
        return new RetrievalHitResponse(
                chunk.getId(),
                chunk.getDocumentId(),
                document.getFilePath(),
                document.getSourceKind(),
                chunk.getChunkType(),
                chunk.getSymbolName(),
                chunk.getStartLine(),
                chunk.getEndLine(),
                candidate.score(),
                candidate.estimatedTokens(),
                candidate.reasons().stream().sorted().toList(),
                excerpt(chunk.getContent())
        );
    }

    private RetrievalTrimmedResponse toTrimmedResponse(TrimmedCandidate value) {
        RetrievalCandidate candidate = value.candidate();
        return new RetrievalTrimmedResponse(
                candidate.chunk().getId(),
                candidate.document().getFilePath(),
                candidate.chunk().getSymbolName(),
                candidate.estimatedTokens(),
                value.reason()
        );
    }

    private String excerpt(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String normalized = content.replace('\u0000', ' ').trim();
        int max = properties.getPreviewCharacters();
        return normalized.length() <= max ? normalized : normalized.substring(0, max) + "…";
    }

    private record TrimmedCandidate(RetrievalCandidate candidate, String reason) {
    }

    private record BudgetedResults(
            List<RetrievalCandidate> selected,
            List<TrimmedCandidate> trimmed,
            int usedTokens
    ) {
    }

    private record ReferenceLoad(List<CodeReference> references, boolean limitReached) {
    }
}
