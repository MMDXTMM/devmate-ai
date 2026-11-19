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
        List<String> targets = RetrievalQueryTargets.identifiers(query);
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
            if (Set.of("IMPORT", "FILE_HEADER").contains(chunk.getChunkType()) && !seedChunkIds.contains(chunk.getId())) continue;
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
            String body = chunk.getContent() == null ? "" : chunk.getContent();
            boolean implementation = "METHOD".equals(chunk.getChunkType()) && body.contains("{")
                    || "TEXT_EXCERPT".equals(chunk.getChunkType()) && Pattern.compile("(?m)^(?:\\s*(?:async )?def |.*(?:function |fn |func ))").matcher(body).find();
            for (String target : targets) {
                String[] parts = target.split("[.#/]");
                String leaf = parts[parts.length - 1];
                String symbol = chunk.getSymbolName() == null ? "" : chunk.getSymbolName();
                boolean member = Pattern.compile("(?<![A-Za-z0-9_$])" + Pattern.quote(leaf) + "\\s*\\(", Pattern.CASE_INSENSITIVE)
                        .matcher(symbol + "\n" + body).find();
                boolean owner = parts.length < 2 || normalize(document.getFilePath()).contains(normalize(parts[parts.length - 2]))
                        || normalize(symbol).contains(normalize(parts[parts.length - 2]));
                if (member && owner) {
                    boolean definition = "METHOD".equals(chunk.getChunkType())
                            && Pattern.compile("#" + Pattern.quote(leaf) + "\\s*\\(", Pattern.CASE_INSENSITIVE).matcher(symbol).find()
                            || "TEXT_EXCERPT".equals(chunk.getChunkType())
                            && Pattern.compile("(?m)^\\s*(?:(?:export|public|async)\\s+)*(?:def|function|fn|func)\\s+"
                                    + Pattern.quote(leaf) + "\\s*\\(", Pattern.CASE_INSENSITIVE).matcher(body).find();
                    score += definition && implementation ? 220 : 40;
                    reasons.add("EXACT_CODE_TARGET");
                    if (definition) reasons.add("EXACT_DEFINITION");
                }
            }
            if (score > 0 && implementation) { score += Math.min(6, Math.log1p(body.length())); reasons.add("IMPLEMENTATION"); }
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
                .sorted(Comparator.comparingInt(LexicalGraphRanker::targetPriority).reversed()
                        .thenComparing(Comparator.comparingDouble(RetrievalCandidate::score).reversed())
                        .thenComparing(candidate -> candidate.document().getFilePath())
                        .thenComparing(candidate -> candidate.chunk().getStartLine()))
                .toList();
    }

    static int targetPriority(RetrievalCandidate candidate) {
        if (!candidate.reasons().contains("EXACT_CODE_TARGET")) return 0;
        if (!candidate.reasons().contains("EXACT_DEFINITION")) return 1;
        return candidate.reasons().contains("IMPLEMENTATION") ? 3 : 2;
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
        List<String> result = new ArrayList<>();
        Arrays.stream(normalized.split("\\s+"))
                .filter(token -> token.length() > 1 && !STOP_WORDS.contains(token)).forEach(token -> {
                    result.add(token);
                    if (token.matches("[\\p{IsHan}]{3,}")) {
                        for (int i = 0; i < token.length() - 1; i++) result.add(token.substring(i, i + 2));
                    }
                });
        return result;

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
