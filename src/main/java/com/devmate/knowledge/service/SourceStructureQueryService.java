package com.devmate.knowledge.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devmate.common.error.BusinessException;
import com.devmate.common.error.ErrorCode;
import com.devmate.knowledge.dto.SourceDocumentResponse;
import com.devmate.knowledge.dto.SourceSymbolResponse;
import com.devmate.knowledge.entity.KnowledgeChunk;
import com.devmate.knowledge.entity.KnowledgeDocument;
import com.devmate.knowledge.mapper.KnowledgeChunkMapper;
import com.devmate.knowledge.mapper.KnowledgeDocumentMapper;
import com.devmate.project.entity.Project;
import com.devmate.project.mapper.ProjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SourceStructureQueryService {

    private final ProjectMapper projectMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final ObjectMapper objectMapper;

    public SourceStructureQueryService(
            ProjectMapper projectMapper,
            KnowledgeDocumentMapper documentMapper,
            KnowledgeChunkMapper chunkMapper,
            ObjectMapper objectMapper
    ) {
        this.projectMapper = projectMapper;
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<SourceDocumentResponse> listDocuments(Long projectId) {
        Project project = requireProject(projectId);
        if (project.getCurrentRevision() == null) {
            return List.of();
        }
        return documentMapper.selectList(Wrappers.lambdaQuery(KnowledgeDocument.class)
                        .eq(KnowledgeDocument::getProjectId, projectId)
                        .eq(KnowledgeDocument::getRevision, project.getCurrentRevision())
                        .orderByAsc(KnowledgeDocument::getFilePath))
                .stream()
                .map(SourceDocumentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SourceSymbolResponse> listSymbols(Long projectId, Long documentId) {
        requireProject(projectId);
        KnowledgeDocument document = documentMapper.selectOne(
                Wrappers.lambdaQuery(KnowledgeDocument.class)
                        .eq(KnowledgeDocument::getId, documentId)
                        .eq(KnowledgeDocument::getProjectId, projectId)
                        .last("LIMIT 1")
        );
        if (document == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "源码文件不存在");
        }
        return chunkMapper.selectList(Wrappers.lambdaQuery(KnowledgeChunk.class)
                        .eq(KnowledgeChunk::getDocumentId, documentId)
                        .orderByAsc(KnowledgeChunk::getChunkIndex))
                .stream()
                .map(chunk -> SourceSymbolResponse.from(chunk, readAnnotations(chunk.getMetadataJson())))
                .toList();
    }

    private Project requireProject(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "项目不存在");
        }
        return project;
    }

    private List<String> readAnnotations(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return List.of();
        }
        try {
            ChunkMetadata metadata = objectMapper.readValue(metadataJson, ChunkMetadata.class);
            return metadata.annotations() == null ? List.of() : List.copyOf(metadata.annotations());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("读取源码符号元数据失败", exception);
        }
    }

    private record ChunkMetadata(List<String> annotations) {
    }
}
