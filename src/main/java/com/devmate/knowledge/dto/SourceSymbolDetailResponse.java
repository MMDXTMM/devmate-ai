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
        int originalCharacters,
        int codeOffset,
        Integer nextOffset
) {
    private static final int MAX_PREVIEW_CHARACTERS = 16_000;

    public static SourceSymbolDetailResponse from(
            KnowledgeChunk chunk,
            List<String> annotations
    ) {
        return from(chunk, annotations, 0);
    }

    public static SourceSymbolDetailResponse from(KnowledgeChunk chunk, List<String> annotations, int offset) {
        String content = chunk.getContent() == null ? "" : chunk.getContent();
        if (offset < 0 || offset > content.length()) throw new com.devmate.common.error.BusinessException(
                com.devmate.common.error.ErrorCode.INVALID_ARGUMENT, "代码读取位置超出片段范围");
        if (offset > 0 && offset < content.length() && Character.isLowSurrogate(content.charAt(offset)))
            throw new com.devmate.common.error.BusinessException(com.devmate.common.error.ErrorCode.INVALID_ARGUMENT, "代码读取位置不在字符边界");
        int end = Math.min(content.length(), offset + MAX_PREVIEW_CHARACTERS);
        if (end < content.length() && Character.isLowSurrogate(content.charAt(end))) end--;
        boolean truncated = end < content.length();
        String preview = content.substring(offset, end);
        int line = (chunk.getStartLine() == null ? 1 : chunk.getStartLine()) + (int)content.substring(0, offset).chars().filter(c -> c == '\n').count();
        return new SourceSymbolDetailResponse(
                chunk.getId(),
                chunk.getDocumentId(),
                chunk.getChunkType(),
                chunk.getSymbolName(),
                List.copyOf(annotations),
                line,
                offset == 0 && !truncated ? chunk.getEndLine() : line + (int)preview.chars().filter(c -> c == '\n').count(),
                chunk.getRevision(),
                preview,
                truncated,
                content.length(),
                offset,
                truncated ? end : null
        );
    }
}
