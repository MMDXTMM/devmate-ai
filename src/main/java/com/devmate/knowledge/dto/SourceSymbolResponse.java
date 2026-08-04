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
                List.copyOf(annotations),
                chunk.getStartLine(),
                chunk.getEndLine(),
                chunk.getContentHash(),
                chunk.getRevision()
        );
    }
}
