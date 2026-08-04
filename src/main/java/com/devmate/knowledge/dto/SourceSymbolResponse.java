package com.devmate.knowledge.dto;

import com.devmate.knowledge.entity.KnowledgeChunk;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.util.List;

public record SourceSymbolResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @JsonSerialize(using = ToStringSerializer.class) Long documentId,
        String chunkType,
        String symbolName,
        String summary,
        List<String> annotations,
        Integer startLine,
        Integer endLine,
        String contentHash,
        String revision
) {
    public static SourceSymbolResponse from(KnowledgeChunk chunk, List<String> annotations) {
        return new SourceSymbolResponse(
                chunk.getId(),
                chunk.getDocumentId(),
                chunk.getChunkType(),
                chunk.getSymbolName(),
                safeSummary(chunk),
                List.copyOf(annotations),
                chunk.getStartLine(),
                chunk.getEndLine(),
                chunk.getContentHash(),
                chunk.getRevision()
        );
    }

    private static String safeSummary(KnowledgeChunk chunk) {
        return chunk.getChunkType().startsWith("CONFIG_")
                || chunk.getChunkType().startsWith("DATABASE_")
                ? chunk.getContent()
                : null;
    }
}
