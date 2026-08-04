package com.devmate.knowledge.retrieval;

import com.devmate.knowledge.entity.KnowledgeChunk;
import com.devmate.knowledge.entity.KnowledgeDocument;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class LexicalGraphRanker {

    private static final Pattern CAMEL_BOUNDARY = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])");
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "by", "for", "from", "in", "is",
            "it", "of", "on", "or", "that", "the", "this", "to", "with", "void", "public",
            "private", "protected", "class", "static", "final", "new", "return"
    );

    public List<RetrievalCandidate> rank(
            Collection<KnowledgeChunk> chunks,
            Map<Long, KnowledgeDocument> documents,
            String query,
            Set<Long> seedChunkIds,
            Map<Long, Set<String>> graphReasons
    ) {
        List<String> queryTerms = new ArrayList<>(new LinkedHashSet<>(tokenize(query)));
        String normalizedQuery = normalize(query);
        Map<Long, List<String>> contentTerms = new HashMap<>();
        Map<String, Integer> documentFrequency = new HashMap<>();

        for (KnowledgeChunk chunk : chunks) {
            List<String> terms = tokenize(searchableText(chunk, documents.get(chunk.getDocumentId())));
            contentTerms.put(chunk.getId(), terms);
            Set<String> unique = new HashSet<>(terms);
            for (String term : queryTerms) {
                if (unique.contains(term)) {
                    documentFrequency.merge(term, 1, Integer::sum);
                }
            }
        }

        List<RetrievalCandidate> ranked = new ArrayList<>();
        int candidateCount = Math.max(chunks.size(), 1);
        for (KnowledgeChunk chunk : chunks) {
            KnowledgeDocument document = documents.get(chunk.getDocumentId());
            if (document == null) {
                continue;
            }
            Set<String> reasons = new LinkedHashSet<>();
            double score = lexicalScore(
                    chunk,
                    document,
                    normalizedQuery,
                    queryTerms,
                    contentTerms.getOrDefault(chunk.getId(), List.of()),
                    documentFrequency,
                    candidateCount,
                    reasons
            );
            if (seedChunkIds.contains(chunk.getId())) {
                score += 100.0;
                reasons.add("DIFF_SYMBOL");
            }
            Set<String> related = graphReasons.get(chunk.getId());
            if (related != null && !related.isEmpty()) {
                score += 24.0 + Math.min(related.size(), 3) * 2.0;
                reasons.addAll(related);
            }
            if (score > 0.0) {
                ranked.add(new RetrievalCandidate(
                        chunk,
                        document,
                        round(score),
                        estimateTokens(chunk.getContent()),
                        Set.copyOf(reasons)
                ));
            }
        }

        return ranked.stream()
                .sorted(Comparator.comparingDouble(RetrievalCandidate::score).reversed()
                        .thenComparing(candidate -> candidate.document().getFilePath())
                        .thenComparing(candidate -> candidate.chunk().getStartLine()))
                .toList();
    }

    private double lexicalScore(
            KnowledgeChunk chunk,
            KnowledgeDocument document,
            String normalizedQuery,
            List<String> queryTerms,
            List<String> terms,
            Map<String, Integer> documentFrequency,
            int candidateCount,
            Set<String> reasons
    ) {
        String symbol = normalize(chunk.getSymbolName());
        String path = normalize(document.getFilePath());
        String content = normalize(chunk.getContent());
        double score = 0.0;

        if (!normalizedQuery.isBlank() && symbol.contains(normalizedQuery)) {
            score += 30.0;
            reasons.add("EXACT_SYMBOL");
        }
        if (!normalizedQuery.isBlank() && path.contains(normalizedQuery)) {
            score += 12.0;
            reasons.add("EXACT_PATH");
        }
        if (!normalizedQuery.isBlank() && content.contains(normalizedQuery)) {
            score += 5.0;
            reasons.add("EXACT_CONTENT");
        }

        Map<String, Integer> termFrequency = new HashMap<>();
        terms.forEach(term -> termFrequency.merge(term, 1, Integer::sum));
        for (String term : queryTerms) {
            boolean matched = false;
            if (symbol.contains(term)) {
                score += 8.0;
                reasons.add("SYMBOL_TERM");
                matched = true;
            }
            if (path.contains(term)) {
                score += 3.0;
                reasons.add("PATH_TERM");
                matched = true;
            }
            int frequency = Math.min(termFrequency.getOrDefault(term, 0), 4);
            if (frequency > 0) {
                double idf = Math.log(1.0 + (candidateCount + 1.0)
                        / (documentFrequency.getOrDefault(term, 0) + 1.0));
                score += frequency * idf;
                reasons.add("CONTENT_TERM");
                matched = true;
            }
            if (!matched && term.length() >= 4 && content.contains(term)) {
                score += 1.0;
                reasons.add("CONTENT_SUBSTRING");
            }
        }
        return score;
    }

    private String searchableText(KnowledgeChunk chunk, KnowledgeDocument document) {
        return String.join(" ",
                value(chunk.getSymbolName()),
                value(chunk.getChunkType()),
                value(chunk.getContent()),
                document == null ? "" : value(document.getFilePath()),
                document == null ? "" : value(document.getSourceKind())
        );
    }

    List<String> tokenize(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        String camelSplit = CAMEL_BOUNDARY.matcher(value).replaceAll(" ");
        String normalized = NON_WORD.matcher(
                Normalizer.normalize(camelSplit, Normalizer.Form.NFKC)
        ).replaceAll(" ").toLowerCase(Locale.ROOT);
        return Arrays.stream(normalized.split("\\s+"))
                .map(String::trim)
                .filter(token -> token.length() > 1)
                .filter(token -> !STOP_WORDS.contains(token))
                .toList();
    }

    int estimateTokens(String content) {
        if (content == null || content.isBlank()) {
            return 1;
        }
        return Math.max(1, (content.codePointCount(0, content.length()) + 3) / 4);
    }

    private String normalize(String value) {
        return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private double round(double score) {
        return Math.round(score * 1000.0) / 1000.0;
    }
}
