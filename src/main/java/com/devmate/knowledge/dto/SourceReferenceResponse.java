package com.devmate.knowledge.dto;

import com.devmate.knowledge.entity.CodeReference;
import com.devmate.knowledge.entity.KnowledgeChunk;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record SourceReferenceResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String referenceKind,
        String referenceName,
        String qualifier,
        Integer argumentCount,
        @JsonSerialize(using = ToStringSerializer.class) Long sourceChunkId,
        String sourceSymbolName,
        String sourceFilePath,
        @JsonSerialize(using = ToStringSerializer.class) Long targetChunkId,
        String targetSymbolName,
        Integer startLine,
        Integer endLine,
        boolean resolved
) {
    public static SourceReferenceResponse from(
            CodeReference reference,
            KnowledgeChunk sourceChunk,
            String sourceFilePath,
            KnowledgeChunk targetChunk
    ) {
        return new SourceReferenceResponse(
                reference.getId(),
                reference.getReferenceKind(),
                reference.getReferenceName(),
                reference.getQualifier(),
                reference.getArgumentCount(),
                sourceChunk.getId(),
                sourceChunk.getSymbolName(),
                sourceFilePath,
                targetChunk == null ? null : targetChunk.getId(),
                targetChunk == null ? null : targetChunk.getSymbolName(),
                reference.getStartLine(),
                reference.getEndLine(),
                targetChunk != null
        );
    }
}
