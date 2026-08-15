package com.devmate.knowledge.dto;

import com.devmate.knowledge.entity.KnowledgeChunk;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.util.List;

public record SourceSymbolDetailResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @JsonSerialize(using = ToStringSerializer.class) Long documentId,
        String chunkType,
        String symbolName,
        List<String> annotations,
        Integer startLine,
        Integer endLine,
        String revision,
        String code,
        boolean truncated,
        int originalCharacters
) {
    private static final int MAX_PREVIEW_CHARACTERS = 16_000;

    public static SourceSymbolDetailResponse from(
            KnowledgeChunk chunk,
            List<String> annotations
    ) {
        String content = chunk.getContent() == null ? "" : chunk.getContent();
        boolean truncated = content.length() > MAX_PREVIEW_CHARACTERS;
        String preview = truncated ? content.substring(0, MAX_PREVIEW_CHARACTERS) : content;
        return new SourceSymbolDetailResponse(
                chunk.getId(),
                chunk.getDocumentId(),
                chunk.getChunkType(),
                chunk.getSymbolName(),
                List.copyOf(annotations),
                chunk.getStartLine(),
                chunk.getEndLine(),
                chunk.getRevision(),
                preview,
                truncated,
                content.length()
        );
    }
}
