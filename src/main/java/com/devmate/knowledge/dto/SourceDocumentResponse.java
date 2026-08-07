package com.devmate.knowledge.dto;

import com.devmate.knowledge.entity.KnowledgeDocument;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record SourceDocumentResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String fileName,
        String filePath,
        String sourceKind,
        String fileType,
        String packageName,
        String revision,
        String structureVersion,
        String status,
        Integer chunkCount
) {
    public static SourceDocumentResponse from(KnowledgeDocument document) {
        return new SourceDocumentResponse(
                document.getId(),
                document.getFileName(),
                document.getFilePath(),
                document.getSourceKind(),
                document.getFileType(),
                document.getPackageName(),
                document.getRevision(),
                document.getStructureVersion(),
                document.getStatus(),
                document.getChunkCount()
        );
    }
}
