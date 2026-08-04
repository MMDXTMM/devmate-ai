package com.devmate.review.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devmate.knowledge.entity.KnowledgeChunk;
import com.devmate.knowledge.entity.KnowledgeDocument;
import com.devmate.knowledge.mapper.KnowledgeChunkMapper;
import com.devmate.knowledge.mapper.KnowledgeDocumentMapper;
import com.devmate.review.dto.MappedSymbolResponse;
import com.devmate.review.model.GitChangedFile;
import com.devmate.review.model.LineRange;
import org.eclipse.jgit.diff.DiffEntry;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DiffSymbolMapper {

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;

    public DiffSymbolMapper(
            KnowledgeDocumentMapper documentMapper,
            KnowledgeChunkMapper chunkMapper
    ) {
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
    }

    public MappedReviewFile map(Long projectId, String targetRevision, GitChangedFile file) {
        String targetPath = file.newPath();
        if (targetPath == null) {
            return new MappedReviewFile(file, "SKIPPED", List.of(), "文件已删除，当前版本没有可映射符号");
        }
        if (!targetPath.endsWith(".java")) {
            return new MappedReviewFile(file, "SKIPPED", List.of(), "当前阶段只映射Java源码");
        }
        KnowledgeDocument document = documentMapper.selectOne(
                Wrappers.lambdaQuery(KnowledgeDocument.class)
                        .eq(KnowledgeDocument::getProjectId, projectId)
                        .eq(KnowledgeDocument::getFilePath, targetPath)
                        .eq(KnowledgeDocument::getRevision, targetRevision)
                        .last("LIMIT 1")
        );
        if (document == null) {
            return new MappedReviewFile(file, "PARTIAL", List.of(), "目标版本尚未建立源码结构");
        }
        List<KnowledgeChunk> chunks = chunkMapper.selectList(
                Wrappers.lambdaQuery(KnowledgeChunk.class)
                        .eq(KnowledgeChunk::getDocumentId, document.getId())
                        .orderByAsc(KnowledgeChunk::getChunkIndex)
        );
        Map<Long, MappedSymbolResponse> mapped = new LinkedHashMap<>();
        int totalChangedLines = 0;
        int mappedChangedLines = 0;
        for (LineRange range : file.targetLineRanges()) {
            for (int line = range.startLine(); line <= range.endLine(); line++) {
                totalChangedLines++;
                boolean lineMapped = false;
                for (KnowledgeChunk chunk : chunks) {
                    if (chunk.getStartLine() <= line && chunk.getEndLine() >= line) {
                        mapped.putIfAbsent(chunk.getId(), new MappedSymbolResponse(
                                chunk.getId(), chunk.getChunkType(), chunk.getSymbolName(),
                                chunk.getStartLine(), chunk.getEndLine()
                        ));
                        lineMapped = true;
                    }
                }
                if (lineMapped) {
                    mappedChangedLines++;
                }
            }
        }

        boolean hasUnmappedDeletion = file.deletions() > 0;
        boolean fullyMapped = totalChangedLines > 0
                && totalChangedLines == mappedChangedLines
                && !hasUnmappedDeletion;
        if (fullyMapped) {
            return new MappedReviewFile(file, "FULL", List.copyOf(mapped.values()), null);
        }
        String reason = hasUnmappedDeletion
                ? "删除行需要在基准版本符号中补充映射"
                : "部分变更行位于可识别类型或方法之外";
        return new MappedReviewFile(file, "PARTIAL", List.copyOf(mapped.values()), reason);
    }
}
