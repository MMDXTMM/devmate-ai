package com.devmate.knowledge.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devmate.agent.config.ProjectUnderstandingProperties;
import com.devmate.knowledge.dto.ProjectGuide;
import com.devmate.knowledge.entity.KnowledgeChunk;
import com.devmate.knowledge.entity.KnowledgeDocument;
import com.devmate.knowledge.mapper.KnowledgeChunkMapper;
import com.devmate.knowledge.mapper.KnowledgeDocumentMapper;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Read-only sampling of the authorized imported snapshot, never the live worktree or target scripts. */
@Service
public class ProjectGuideEvidenceService {
    private final KnowledgeDocumentMapper documents;
    private final KnowledgeChunkMapper chunks;
    private final ProjectUnderstandingProperties properties;
    public ProjectGuideEvidenceService(KnowledgeDocumentMapper documents, KnowledgeChunkMapper chunks,
                                       ProjectUnderstandingProperties properties) {
        this.documents = documents; this.chunks = chunks; this.properties = properties;
    }
    public Sample sample(Long projectId, String revision) {
        var all = documents.selectList(Wrappers.lambdaQuery(KnowledgeDocument.class)
                .eq(KnowledgeDocument::getProjectId, projectId).eq(KnowledgeDocument::getRevision, revision)
                .orderByAsc(KnowledgeDocument::getFilePath).last("LIMIT 3000"));
        var sorted = all.stream().sorted(Comparator.comparingInt(this::priority)
                .thenComparing(Comparator.comparingInt((KnowledgeDocument doc) ->
                        "JAVA".equals(doc.getFileType()) && doc.getChunkCount() != null ? doc.getChunkCount() : 0).reversed())
                .thenComparing(KnowledgeDocument::getFilePath)).toList();
        // Round robin between subprojects/source/test/docs prevents a single directory consuming the budget.
        Map<String, Queue<KnowledgeDocument>> groups = new LinkedHashMap<>();
        for (var doc : sorted) {
            String path = doc.getFilePath();
            String group = priority(doc) == 0 ? "00-overview" : path.split("/")[0] + ":" + role(path) + ":" + priority(doc);
            groups.computeIfAbsent(group, key -> new ArrayDeque<>()).add(doc);
        }
        List<ProjectGuide.Evidence> evidence = new ArrayList<>();
        int characters = 0;
        int inspected = 0;
        int maxChunks = Math.max(1, Math.min(properties.getMaxEvidenceChunks(), 100));
        int maxChars = Math.max(1000, Math.min(properties.getMaxEvidenceCharacters(), 100000));
        while (groups.values().stream().anyMatch(queue -> !queue.isEmpty()) && evidence.size() < maxChunks
                && characters < maxChars && inspected < 150) {
            for (var queue : groups.values()) {
                if (queue.isEmpty() || evidence.size() >= maxChunks || characters >= maxChars || inspected >= 150) continue;
                KnowledgeDocument doc = queue.remove(); inspected++;
                boolean javaSource = "JAVA".equals(doc.getFileType());
                var candidates = chunks.selectList(Wrappers.lambdaQuery(KnowledgeChunk.class)
                        .eq(KnowledgeChunk::getProjectId, projectId).eq(KnowledgeChunk::getRevision, revision)
                        .eq(KnowledgeChunk::getDocumentId, doc.getId())
                        .in(javaSource, KnowledgeChunk::getChunkType, List.of("METHOD", "CLASS", "CONSTRUCTOR"))
                        .orderByAsc(KnowledgeChunk::getChunkIndex).last(javaSource ? "LIMIT 12" : "LIMIT 2"));
                if ("JAVA".equals(doc.getFileType())) candidates = candidates.stream()
                        .sorted(Comparator.comparingInt((KnowledgeChunk item) -> switch (item.getChunkType()) {
                            case "METHOD" -> 0; case "CLASS" -> 1; case "CONSTRUCTOR" -> 2; default -> 3;
                        }).thenComparing(Comparator.comparingInt((KnowledgeChunk item) ->
                                item.getContent() == null ? 0 : item.getContent().length()).reversed())).limit(2).toList();
                for (var chunk : candidates) {
                    if (evidence.size() >= maxChunks || characters >= maxChars) break;
                    if (chunk.getContent() == null || chunk.getContent().isBlank()) continue;
                    String text = chunk.getContent();
                    int budget = Math.min(1800, maxChars - characters);
                    String preview = text.substring(0, Math.min(budget, text.length()));
                    evidence.add(new ProjectGuide.Evidence(String.valueOf(chunk.getId()), doc.getSourceKind(), revision,
                            chunk.getSymbolName(), doc.getFilePath(), chunk.getStartLine(), chunk.getEndLine(),
                            preview, preview.length() < text.length()));
                    characters += preview.length();
                }
            }
        }
        StringBuilder digestInput = new StringBuilder(revision).append("|text-excerpts-v1|guide-sample-v1");
        all.forEach(doc -> digestInput.append('\n').append(doc.getFilePath()).append(':').append(doc.getContentHash()));
        String digest;
        try { digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(digestInput.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
        var coverage = new ProjectGuide.Coverage(all.size(), (int)evidence.stream().map(ProjectGuide.Evidence::filePath).distinct().count(),
                evidence.size(), characters, maxChunks, maxChars, List.of(
                "按导入快照中的说明、清单、源码与测试抽样；模型目录列表仅提供前 150 个路径，目录不代表已深入读取。",
                "跳过依赖、构建产物、凭据、二进制和非 UTF-8；仅支持白名单文本类型，最多列出 3000 个已索引文件。",
                "每个文件最多抽样两个片段；Java 优先业务方法，长片段截断；未读取的实现和外部系统不能确认。",
                "未运行目标项目或其测试；已实现表示源码观察，不表示运行验收通过。",
                "历史导入可能仅含 Java/配置/迁移；新导入才包含通用文本。"));
        return new Sample(new ProjectGuide.Snapshot(revision, digest, "guide-sample-v1/text-excerpts-v1"),
                coverage, List.copyOf(evidence), all.stream().map(KnowledgeDocument::getFilePath).toList());
    }
    private String role(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.contains("/controller/") || lower.contains("/routes/")) return "entry";
        if (lower.contains("/impl/") || lower.contains("/services/")) return "implementation";
        if (lower.contains("/service/")) return "service";
        if (lower.contains("/mapper/") || lower.contains("/repository/")) return "data";
        if (lower.contains("/entity/") || lower.contains("/dto/")) return "model";
        if (lower.contains("/config/")) return "configuration";
        return "other";
    }
    private int priority(KnowledgeDocument doc) {
        String path = doc.getFilePath().toLowerCase(Locale.ROOT);
        if (path.matches("(?:.*/)?(?:readme[^/]*|package.json|pom.xml|pyproject.toml|go.mod|cargo.toml|requirements.txt)$")) return 0;
        if (path.contains("test") || path.contains("spec")) return 3;
        if (path.contains("main") || path.contains("index") || path.contains("app") || path.contains("cli")) return 1;
        if ("DOCUMENTATION".equals(doc.getSourceKind())) return 4;
        return 2;
    }
    public record Sample(ProjectGuide.Snapshot snapshot, ProjectGuide.Coverage coverage,
                         List<ProjectGuide.Evidence> evidence, List<String> indexedPaths) { }
}
