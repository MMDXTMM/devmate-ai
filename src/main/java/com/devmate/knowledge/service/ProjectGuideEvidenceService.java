package com.devmate.knowledge.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devmate.agent.config.ProjectUnderstandingProperties;
import com.devmate.knowledge.dto.ProjectGuide;
import com.devmate.knowledge.entity.KnowledgeChunk;
import com.devmate.knowledge.entity.KnowledgeDocument;
import com.devmate.knowledge.mapper.KnowledgeChunkMapper;
import com.devmate.knowledge.mapper.KnowledgeDocumentMapper;
import com.devmate.knowledge.source.ProjectReadme;
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
        String primaryReadme = ProjectReadme.primary(all.stream().map(KnowledgeDocument::getFilePath).toList()).orElse(null);
        // Read the project introduction first, then round robin between implementation roles.
        List<KnowledgeDocument> readOrder = new ArrayList<>();
        sorted.stream().filter(doc -> doc.getFilePath().equals(primaryReadme)).findFirst().ifPresent(readOrder::add);
        Map<String, Queue<KnowledgeDocument>> groups = new LinkedHashMap<>();
        for (var doc : sorted) {
            String path = doc.getFilePath();
            if (path.equals(primaryReadme)) continue;
            String group = priority(doc) == 0 ? "00-overview" : path.split("/")[0] + ":" + role(path) + ":" + priority(doc);
            groups.computeIfAbsent(group, key -> new ArrayDeque<>()).add(doc);
        }
        while (groups.values().stream().anyMatch(queue -> !queue.isEmpty()) && readOrder.size() < 150) {
            for (var queue : groups.values()) {
                if (!queue.isEmpty() && readOrder.size() < 150) readOrder.add(queue.remove());
            }
        }
        List<ProjectGuide.Evidence> evidence = new ArrayList<>();
        int characters = 0;
        int maxChunks = Math.max(1, Math.min(properties.getMaxEvidenceChunks(), 100));
        int maxChars = Math.max(1000, Math.min(properties.getMaxEvidenceCharacters(), 100000));
        for (var doc : readOrder) {
            if (evidence.size() >= maxChunks || characters >= maxChars) break;
            boolean introduction = doc.getFilePath().equals(primaryReadme);
            // Leave room for source verification even when the global character budget is small.
            int documentBudget = introduction && all.size() > 1 ? Math.min(7200, maxChars / 3) : maxChars;
            int documentCharacters = 0;
            boolean javaSource = "JAVA".equals(doc.getFileType());
            var candidates = chunks.selectList(Wrappers.lambdaQuery(KnowledgeChunk.class)
                    .eq(KnowledgeChunk::getProjectId, projectId).eq(KnowledgeChunk::getRevision, revision)
                    .eq(KnowledgeChunk::getDocumentId, doc.getId())
                    .in(javaSource, KnowledgeChunk::getChunkType, List.of("METHOD", "CLASS", "CONSTRUCTOR"))
                    .orderByAsc(KnowledgeChunk::getChunkIndex).last(javaSource ? "LIMIT 12" : introduction ? "LIMIT 4" : "LIMIT 2"));
            if ("JAVA".equals(doc.getFileType())) candidates = candidates.stream()
                    .sorted(Comparator.comparingInt((KnowledgeChunk item) -> switch (item.getChunkType()) {
                        case "METHOD" -> 0; case "CLASS" -> 1; case "CONSTRUCTOR" -> 2; default -> 3;
                    }).thenComparing(Comparator.comparingInt((KnowledgeChunk item) ->
                            item.getContent() == null ? 0 : item.getContent().length()).reversed())).limit(2).toList();
            for (var chunk : candidates) {
                if (evidence.size() >= maxChunks || characters >= maxChars || documentCharacters >= documentBudget) break;
                if (chunk.getContent() == null || chunk.getContent().isBlank()) continue;
                String text = chunk.getContent();
                int budget = Math.min(1800, Math.min(maxChars - characters, documentBudget - documentCharacters));
                String preview = text.substring(0, Math.min(budget, text.length()));
                evidence.add(new ProjectGuide.Evidence(String.valueOf(chunk.getId()), doc.getSourceKind(), revision,
                        chunk.getSymbolName(), doc.getFilePath(), chunk.getStartLine(), chunk.getEndLine(),
                        preview, preview.length() < text.length()));
                characters += preview.length();
                documentCharacters += preview.length();
            }
        }
        StringBuilder digestInput = new StringBuilder(revision).append("|text-excerpts-v1|readme-first-v2");
        all.forEach(doc -> digestInput.append('\n').append(doc.getFilePath()).append(':').append(doc.getContentHash()));
        String digest;
        try { digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(digestInput.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
        long readmeChunks = evidence.stream().filter(item -> item.filePath().equals(primaryReadme)).count();
        var coverage = new ProjectGuide.Coverage(all.size(), (int)evidence.stream().map(ProjectGuide.Evidence::filePath).distinct().count(),
                evidence.size(), characters, maxChunks, maxChars, List.of(
                primaryReadme == null ? "当前导入快照未索引到 README，使用清单、其他文档与源码解释；不会猜测项目介绍。"
                        : readmeChunks == 0 ? "已优先检查项目介绍 " + primaryReadme + "，但索引中没有可读正文；改用其他证据解释。"
                        : "优先读取项目介绍 " + primaryReadme + " 的 " + readmeChunks + " 个片段，再用源码核对；README 为文档描述，不是功能已实现或已运行的证明。",
                "按导入快照抽样；模型目录列表仅提供前 150 个路径，目录不代表已深入读取。",
                "跳过依赖、构建产物、凭据、二进制和非 UTF-8；仅支持白名单文本类型，最多列出 3000 个已索引文件。",
                "主要 README 最多四个片段，其余文件最多两个；Java 优先业务方法，长片段截断；未读取的实现和外部系统不能确认。",
                "未运行目标项目或其测试；已实现表示源码观察，不表示运行验收通过。",
                "历史导入可能仅含 Java/配置/迁移；新导入才包含通用文本。"));
        return new Sample(new ProjectGuide.Snapshot(revision, digest, "readme-first-v2/text-excerpts-v1"),
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
