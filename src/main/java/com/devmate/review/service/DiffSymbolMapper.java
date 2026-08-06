package com.devmate.review.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devmate.knowledge.entity.KnowledgeChunk;
import com.devmate.knowledge.entity.KnowledgeDocument;
import com.devmate.knowledge.mapper.KnowledgeChunkMapper;
import com.devmate.knowledge.mapper.KnowledgeDocumentMapper;
import com.devmate.knowledge.source.JavaSourceParser;
import com.devmate.knowledge.source.ParsedSourceChunk;
import com.devmate.review.dto.MappedSymbolResponse;
import com.devmate.review.model.GitChangedFile;
import com.devmate.review.model.LineRange;
import com.devmate.review.source.GitRevisionSourceReader;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DiffSymbolMapper {

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final GitRevisionSourceReader revisionSourceReader;
    private final JavaSourceParser sourceParser;

    public DiffSymbolMapper(
            KnowledgeDocumentMapper documentMapper,
            KnowledgeChunkMapper chunkMapper,
            GitRevisionSourceReader revisionSourceReader,
            JavaSourceParser sourceParser
    ) {
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.revisionSourceReader = revisionSourceReader;
        this.sourceParser = sourceParser;
    }

    public MappedReviewFile map(
            Long projectId,
            Path repositoryRoot,
            String baseRevision,
            String targetRevision,
            GitChangedFile file
    ) {
        String javaPath = file.newPath() != null ? file.newPath() : file.oldPath();
        if (javaPath == null || !javaPath.endsWith(".java")) {
            return new MappedReviewFile(file, "SKIPPED", List.of(), "当前阶段只映射Java源码");
        }

        MappingAccumulator accumulator = new MappingAccumulator();
        mapTarget(projectId, targetRevision, file, accumulator);
        mapBase(repositoryRoot, baseRevision, file, accumulator);

        int totalLines = accumulator.totalLines;
        boolean fullyMapped = totalLines == accumulator.mappedLines;
        if (fullyMapped) {
            return new MappedReviewFile(file, "FULL", accumulator.symbols(), null);
        }
        return new MappedReviewFile(
                file,
                "PARTIAL",
                accumulator.symbols(),
                "部分变更行位于可识别类型或方法之外，或对应版本源码不可用"
        );
    }

    private void mapTarget(
            Long projectId,
            String targetRevision,
            GitChangedFile file,
            MappingAccumulator accumulator
    ) {
        if (file.targetLineRanges().isEmpty()) {
            return;
        }
        if (file.newPath() == null || !file.newPath().endsWith(".java")) {
            accumulator.addUnmapped(file.targetLineRanges());
            return;
        }
        String targetPath = file.newPath();
        KnowledgeDocument document = documentMapper.selectOne(
                Wrappers.lambdaQuery(KnowledgeDocument.class)
                        .eq(KnowledgeDocument::getProjectId, projectId)
                        .eq(KnowledgeDocument::getPathHash, sha256(targetPath))
                        .eq(KnowledgeDocument::getRevision, targetRevision)
                        .last("LIMIT 1")
        );
        if (document == null || !targetPath.equals(document.getFilePath())) {
            accumulator.addUnmapped(file.targetLineRanges());
            return;
        }
        List<KnowledgeChunk> chunks = chunkMapper.selectList(
                Wrappers.lambdaQuery(KnowledgeChunk.class)
                        .eq(KnowledgeChunk::getDocumentId, document.getId())
                        .orderByAsc(KnowledgeChunk::getChunkIndex)
        );
        accumulator.mapPersisted(file.targetLineRanges(), chunks);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前JDK不支持SHA-256", exception);
        }
    }

    private void mapBase(
            Path repositoryRoot,
            String baseRevision,
            GitChangedFile file,
            MappingAccumulator accumulator
    ) {
        if (file.baseLineRanges().isEmpty()) {
            return;
        }
        if (file.oldPath() == null || !file.oldPath().endsWith(".java")) {
            accumulator.addUnmapped(file.baseLineRanges());
            return;
        }
        revisionSourceReader.read(repositoryRoot, baseRevision, file.oldPath())
                .map(source -> sourceParser.parseContent(file.oldPath(), source).chunks())
                .ifPresentOrElse(
                        chunks -> accumulator.mapTransient(file.baseLineRanges(), chunks),
                        () -> accumulator.addUnmapped(file.baseLineRanges())
                );
    }

    private static final class MappingAccumulator {

        private final Map<String, MappedSymbolResponse> mapped = new LinkedHashMap<>();
        private int totalLines;
        private int mappedLines;

        private void mapPersisted(List<LineRange> ranges, List<KnowledgeChunk> chunks) {
            forEachLine(ranges, line -> {
                boolean found = false;
                for (KnowledgeChunk chunk : chunks) {
                    if (contains(chunk.getStartLine(), chunk.getEndLine(), line)) {
                        String key = "TARGET:" + chunk.getId();
                        mapped.putIfAbsent(key, new MappedSymbolResponse(
                                chunk.getId(), "TARGET", chunk.getChunkType(), chunk.getSymbolName(),
                                chunk.getStartLine(), chunk.getEndLine()
                        ));
                        found = true;
                    }
                }
                return found;
            });
        }

        private void mapTransient(List<LineRange> ranges, List<ParsedSourceChunk> chunks) {
            forEachLine(ranges, line -> {
                boolean found = false;
                for (ParsedSourceChunk chunk : chunks) {
                    if (contains(chunk.startLine(), chunk.endLine(), line)) {
                        String key = "BASE:" + chunk.chunkIndex() + ":" + chunk.symbolName();
                        mapped.putIfAbsent(key, new MappedSymbolResponse(
                                null, "BASE", chunk.chunkType(), chunk.symbolName(),
                                chunk.startLine(), chunk.endLine()
                        ));
                        found = true;
                    }
                }
                return found;
            });
        }

        private void forEachLine(List<LineRange> ranges, LineMapper mapper) {
            for (LineRange range : ranges) {
                for (int line = range.startLine(); line <= range.endLine(); line++) {
                    totalLines++;
                    if (mapper.map(line)) {
                        mappedLines++;
                    }
                }
            }
        }

        private void addUnmapped(List<LineRange> ranges) {
            forEachLine(ranges, line -> false);
        }

        private boolean contains(Integer startLine, Integer endLine, int line) {
            return startLine != null && endLine != null && startLine <= line && endLine >= line;
        }

        private List<MappedSymbolResponse> symbols() {
            return List.copyOf(mapped.values());
        }
    }

    @FunctionalInterface
    private interface LineMapper {
        boolean map(int line);
    }
}
