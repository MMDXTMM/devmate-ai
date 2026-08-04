package com.devmate.knowledge.dto;

import com.devmate.knowledge.entity.KnowledgeDocument;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record SourceDocumentResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String fileName,
        String filePath,
        String packageName,
        String revision,
        String status,
        Integer chunkCount
) {
    public static SourceDocumentResponse from(KnowledgeDocument document) {
        return new SourceDocumentResponse(
                document.getId(),
                document.getFileName(),
                document.getFilePath(),
                document.getPackageName(),
                document.getRevision(),
                document.getStatus(),
                document.getChunkCount()
        );
    }
}
