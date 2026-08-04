package com.devmate.knowledge.retrieval;

import com.devmate.knowledge.entity.KnowledgeChunk;
import com.devmate.knowledge.entity.KnowledgeDocument;
import com.devmate.knowledge.vector.VectorMatch;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class HybridRetrievalRanker {

    private static final double RRF_K = 60.0;

    public List<RetrievalCandidate> fuse(
            RetrievalMode mode,
            List<RetrievalCandidate> lexical,
            List<VectorMatch> vectorMatches,
            Map<Long, KnowledgeChunk> chunks,
            Map<Long, KnowledgeDocument> documents
    ) {
        if (mode == RetrievalMode.LEXICAL || vectorMatches.isEmpty()) {
            return lexical;
        }
        Map<Long, FusionState> states = new LinkedHashMap<>();
        Map<Long, RetrievalCandidate> lexicalById = new LinkedHashMap<>();
        lexical.forEach(candidate -> lexicalById.put(candidate.chunk().getId(), candidate));

        for (int index = 0; index < lexical.size(); index++) {
            RetrievalCandidate candidate = lexical.get(index);
            if (mode == RetrievalMode.HYBRID || isDeterministicContext(candidate.reasons())) {
                FusionState state = states.computeIfAbsent(
                        candidate.chunk().getId(), ignored -> FusionState.from(candidate)
                );
                state.score += reciprocalRank(index);
                state.reasons.add(mode == RetrievalMode.HYBRID
                        ? "LEXICAL_RANK"
                        : "DETERMINISTIC_CONTEXT");
            }
        }

        for (int index = 0; index < vectorMatches.size(); index++) {
            VectorMatch match = vectorMatches.get(index);
            FusionState state = states.get(match.chunkId());
            if (state == null) {
                state = createState(chunks.get(match.chunkId()), documents);
                if (state == null) {
                    continue;
                }
                states.put(match.chunkId(), state);
            }
            state.score += reciprocalRank(index);
            state.reasons.add("VECTOR_SIMILARITY");
            state.reasons.add("COSINE_" + String.format(Locale.ROOT, "%.3f", match.similarity()));
            RetrievalCandidate lexicalCandidate = lexicalById.get(match.chunkId());
            if (lexicalCandidate != null) {
                state.reasons.addAll(lexicalCandidate.reasons());
            }
        }

        List<RetrievalCandidate> result = new ArrayList<>();
        for (FusionState state : states.values()) {
            result.add(new RetrievalCandidate(
                    state.chunk,
                    state.document,
                    Math.round(state.score * 1_000_000.0) / 1000.0,
                    state.estimatedTokens,
                    Set.copyOf(state.reasons)
            ));
        }
        return result.stream()
                .sorted(Comparator.comparingDouble(RetrievalCandidate::score).reversed()
                        .thenComparing(candidate -> candidate.document().getFilePath())
                        .thenComparing(candidate -> candidate.chunk().getStartLine()))
                .toList();
    }

    private double reciprocalRank(int zeroBasedRank) {
        return 1.0 / (RRF_K + zeroBasedRank + 1.0);
    }

    private boolean isDeterministicContext(Set<String> reasons) {
        return reasons.stream().anyMatch(reason -> reason.equals("DIFF_SYMBOL")
                || reason.startsWith("INCOMING_")
                || reason.startsWith("OUTGOING_"));
    }

    private FusionState createState(
            KnowledgeChunk chunk,
            Map<Long, KnowledgeDocument> documents
    ) {
        if (chunk == null) {
            return null;
        }
        KnowledgeDocument document = documents.get(chunk.getDocumentId());
        if (document == null) {
            return null;
        }
        String content = chunk.getContent() == null ? "" : chunk.getContent();
        int tokens = Math.max(1, (content.codePointCount(0, content.length()) + 3) / 4);
        return new FusionState(chunk, document, tokens, new LinkedHashSet<>(), 0.0);
    }

    private static final class FusionState {
        private final KnowledgeChunk chunk;
        private final KnowledgeDocument document;
        private final int estimatedTokens;
        private final Set<String> reasons;
        private double score;

        private FusionState(
                KnowledgeChunk chunk,
                KnowledgeDocument document,
                int estimatedTokens,
                Set<String> reasons,
                double score
        ) {
            this.chunk = chunk;
            this.document = document;
            this.estimatedTokens = estimatedTokens;
            this.reasons = reasons;
            this.score = score;
        }

        private static FusionState from(RetrievalCandidate candidate) {
            return new FusionState(
                    candidate.chunk(), candidate.document(), candidate.estimatedTokens(),
                    new LinkedHashSet<>(candidate.reasons()), 0.0
            );
        }
    }
}
