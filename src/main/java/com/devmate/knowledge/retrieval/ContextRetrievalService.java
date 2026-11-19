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
    private final com.devmate.knowledge.service.ProjectUnderstandingReportService reports;

    public ContextRetrievalService(
            ProjectService projectService,
            KnowledgeChunkMapper chunkMapper,
            KnowledgeDocumentMapper documentMapper,
            CodeReferenceMapper referenceMapper,
            LexicalGraphRanker ranker,
            HybridRetrievalRanker hybridRanker,
            VectorRetrievalService vectorRetrievalService,
            RetrievalProperties properties,
            com.devmate.knowledge.service.ProjectUnderstandingReportService reports
    ) {
        this.projectService = projectService;
        this.chunkMapper = chunkMapper;
        this.documentMapper = documentMapper;
        this.referenceMapper = referenceMapper;
        this.ranker = ranker;
        this.hybridRanker = hybridRanker;
        this.vectorRetrievalService = vectorRetrievalService;
        this.properties = properties;
        this.reports = reports;
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

        // Search the whole authorized revision in SQL before applying the candidate budget.
        // A matching symbol/path late in an import must not depend on its generated ID.
        List<KnowledgeChunk> loaded = loadRelevantCandidates(projectId, revision, command.query());
        if (loaded.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "该项目版本尚未建立知识索引");
        }
        boolean candidateLimitReached = loaded.size() > properties.getCandidateLimit();
        List<KnowledgeChunk> candidates = new ArrayList<>(loaded.stream()
                .limit(properties.getCandidateLimit()).toList());

        boolean topicSeeds = (command.seedChunkIds() == null || command.seedChunkIds().isEmpty())
                && !RetrievalQueryTargets.hasExplicitTarget(command.query())
                && org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication() != null
                && org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal()
                    instanceof com.devmate.user.security.AuthenticatedUser;
        List<Long> requestedSeeds = topicSeeds ? reports.conversationSeeds(projectId, revision, command.query()) : command.seedChunkIds();
        Set<Long> seedIds = validateAndLoadSeeds(
                projectId,
                revision,
                requestedSeeds,
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
        String searchQuery = command.query().trim();
        if (topicSeeds && !seedIds.isEmpty()) {
            // The saved report maps this project's vocabulary to source, never a global translation table.
            String aliases = candidates.stream().filter(item -> seedIds.contains(item.getId()))
                    .map(KnowledgeChunk::getSymbolName).filter(Objects::nonNull)
                    .map(name -> name.contains("#") ? name.substring(name.lastIndexOf('#') + 1).replaceAll("\\(.*", "") : "")
                    .filter(name -> name.length() > 3).distinct().limit(6).collect(Collectors.joining(" "));
            searchQuery += " " + aliases;
        }
        List<RetrievalCandidate> lexicalRanked = ranker.rank(
                candidates,
                documents,
                searchQuery,
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
        if (requestedMode == RetrievalMode.HYBRID && "LOCAL".equals(embeddingProvider.providerName())) {
            Set<Long> relevant = lexicalRanked.stream().map(item -> item.chunk().getId()).collect(Collectors.toSet());
            ranked = ranked.stream().filter(item -> relevant.contains(item.chunk().getId())).toList();
        }
        if (topicSeeds && !seedIds.isEmpty()) ranked = ranked.stream().map(item -> {
            Set<String> reasons = new LinkedHashSet<>(item.reasons());
            if (reasons.remove("DIFF_SYMBOL")) reasons.add("BUSINESS_TOPIC");
            return new RetrievalCandidate(item.chunk(), item.document(), item.score(), item.estimatedTokens(), Set.copyOf(reasons));
        }).toList();
        // Budget the actual excerpt passed downstream, not a full long method that would otherwise
        // be excluded before read_code ever gets a chance to expand it.
        String originalQuery = command.query();
        ranked = ranked.stream().map(candidate -> {
            String excerpt = preview(candidate.chunk(), originalQuery).text();
            int tokens = Math.max(1, (excerpt.codePointCount(0, excerpt.length()) + 1) / 2);
            return new RetrievalCandidate(candidate.chunk(), candidate.document(), candidate.score(), tokens, candidate.reasons());
        }).toList();
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
                budgeted.selected().stream().map(item -> toHitResponse(item, command.query())).toList(),
                trimmedDetails
        );
    }

    private List<KnowledgeChunk> loadRelevantCandidates(Long projectId, String revision, String query) {
        int limit = properties.getCandidateLimit();
        Map<Long, KnowledgeChunk> selected = new LinkedHashMap<>();
        List<String> targets = RetrievalQueryTargets.identifiers(query);
        List<String> terms = ranker.tokenize(query).stream().distinct().limit(16).toList();
        List<Long> pathDocuments = terms.isEmpty() ? List.of() : documentMapper.selectList(Wrappers.lambdaQuery(KnowledgeDocument.class)
                .select(KnowledgeDocument::getId)
                .eq(KnowledgeDocument::getProjectId, projectId)
                .eq(KnowledgeDocument::getRevision, revision)
                .and(!terms.isEmpty(), wrapper -> {
                    for (String term : terms) wrapper.or().apply("LOCATE({0}, LOWER(file_path)) > 0", term);
                }).orderByAsc(KnowledgeDocument::getId).last("LIMIT " + limit))
                .stream().map(KnowledgeDocument::getId).toList();
        if (!targets.isEmpty()) {
            List<Long> explicitDocuments = documentMapper.selectList(Wrappers.lambdaQuery(KnowledgeDocument.class)
                    .select(KnowledgeDocument::getId)
                    .eq(KnowledgeDocument::getProjectId, projectId).eq(KnowledgeDocument::getRevision, revision)
                    .and(wrapper -> targets.forEach(target -> wrapper.or()
                            .apply("LOCATE({0}, LOWER(file_path)) > 0", target.toLowerCase(java.util.Locale.ROOT))))
                    .orderByAsc(KnowledgeDocument::getId).last("LIMIT " + limit))
                    .stream().map(KnowledgeDocument::getId).toList();
            if (!explicitDocuments.isEmpty()) chunkMapper.selectList(Wrappers.lambdaQuery(KnowledgeChunk.class)
                    .eq(KnowledgeChunk::getProjectId, projectId).eq(KnowledgeChunk::getRevision, revision)
                    .in(KnowledgeChunk::getDocumentId, explicitDocuments)
                    .orderByAsc(KnowledgeChunk::getId).last("LIMIT " + (limit + 1)))
                    .forEach(chunk -> selected.putIfAbsent(chunk.getId(), chunk));
            var exact = Wrappers.lambdaQuery(KnowledgeChunk.class)
                    .eq(KnowledgeChunk::getProjectId, projectId).eq(KnowledgeChunk::getRevision, revision);
            exact.and(wrapper -> {
                for (String target : targets) {
                    String[] parts = target.toLowerCase(java.util.Locale.ROOT).split("[.#/]");
                    String leaf = parts[parts.length - 1];
                    wrapper.or(nested -> {
                        nested.apply("LOCATE({0}, LOWER(symbol_name)) > 0", leaf);
                        if (parts.length > 1) nested.apply("LOCATE({0}, LOWER(symbol_name)) > 0", parts[parts.length-2]);
                    });
                }
            });
            chunkMapper.selectList(exact.orderByAsc(KnowledgeChunk::getId).last("LIMIT " + (limit + 1)))
                    .forEach(chunk -> selected.putIfAbsent(chunk.getId(), chunk));
        }
        if (selected.size() <= limit && (!terms.isEmpty() || !pathDocuments.isEmpty())) {
            var matching = Wrappers.lambdaQuery(KnowledgeChunk.class)
                    .eq(KnowledgeChunk::getProjectId, projectId).eq(KnowledgeChunk::getRevision, revision);
            matching.and(wrapper -> {
                for (String term : terms) {
                    wrapper.or().apply("LOCATE({0}, LOWER(symbol_name)) > 0 OR LOCATE({0}, LOWER(content)) > 0", term);
                }
                if (!pathDocuments.isEmpty()) wrapper.or().in(KnowledgeChunk::getDocumentId, pathDocuments);
            });
            chunkMapper.selectList(matching.orderByAsc(KnowledgeChunk::getId).last("LIMIT " + (limit + 1)))
                    .forEach(chunk -> selected.putIfAbsent(chunk.getId(), chunk));
        }
        // Preserve seed-only queries and the existing not-indexed error for empty projects.
        if (selected.isEmpty()) chunkMapper.selectList(Wrappers.lambdaQuery(KnowledgeChunk.class)
                .eq(KnowledgeChunk::getProjectId, projectId).eq(KnowledgeChunk::getRevision, revision)
                .orderByAsc(KnowledgeChunk::getId).last("LIMIT " + (limit + 1)))
                .forEach(chunk -> selected.putIfAbsent(chunk.getId(), chunk));
        return List.copyOf(selected.values());
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

    private RetrievalHitResponse toHitResponse(RetrievalCandidate candidate, String query) {
        KnowledgeChunk chunk = candidate.chunk();
        KnowledgeDocument document = candidate.document();
        Preview preview = preview(chunk, query);
        return new RetrievalHitResponse(
                chunk.getId(),
                chunk.getDocumentId(),
                document.getFilePath(),
                document.getSourceKind(),
                chunk.getChunkType(),
                chunk.getSymbolName(),
                preview.startLine(),
                preview.endLine(),
                candidate.score(),
                candidate.estimatedTokens(),
                candidate.reasons().stream().sorted().toList(),
                preview.text()
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

    private Preview preview(KnowledgeChunk chunk, String query) {
        String text = chunk.getContent() == null ? "" : chunk.getContent().replace('\u0000', ' ');
        int offset = 0;
        var targets = RetrievalQueryTargets.identifiers(query);
        for (String target : targets) {
            String[] parts = target.split("[.#/]");
            var match = java.util.regex.Pattern.compile("(?<![A-Za-z0-9_$])" + java.util.regex.Pattern.quote(parts[parts.length - 1]) + "\\s*\\(")
                    .matcher(text);
            if (match.find()) { offset = Math.max(0, text.lastIndexOf('\n', Math.max(0, match.start() - 120)) + 1); break; }
        }
        int end = Math.min(text.length(), offset + properties.getPreviewCharacters());
        int startLine = (chunk.getStartLine() == null ? 1 : chunk.getStartLine()) + (int)text.substring(0, offset).chars().filter(c -> c == '\n').count();
        String preview = text.substring(offset, end);
        int endLine = startLine + (int)preview.chars().filter(c -> c == '\n').count()
                - (preview.endsWith("\n") ? 1 : 0);
        endLine = Math.max(startLine, chunk.getEndLine() == null ? endLine : Math.min(chunk.getEndLine(), endLine));
        return new Preview(preview + (end < text.length() ? "…" : ""), startLine, endLine);
    }
    private record Preview(String text, int startLine, int endLine) { }

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
