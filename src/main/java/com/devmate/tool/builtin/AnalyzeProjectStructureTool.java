package com.devmate.tool.builtin;

import com.devmate.agent.config.ReviewAgentProperties;
import com.devmate.agent.model.ReviewAgentToolDefinition;
import com.devmate.knowledge.dto.SourceDocumentResponse;
import com.devmate.knowledge.service.SourceStructureQueryService;
import com.devmate.tool.AgentTool;
import com.devmate.tool.model.AgentToolExecutionException;
import com.devmate.tool.model.AgentToolResult;
import com.devmate.tool.model.ReviewAgentContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class AnalyzeProjectStructureTool implements AgentTool {

    public static final String NAME = "analyzeProjectStructure";

    private final SourceStructureQueryService queryService;
    private final ReviewAgentProperties properties;
    private final ObjectMapper objectMapper;

    public AnalyzeProjectStructureTool(
            SourceStructureQueryService queryService,
            ReviewAgentProperties properties,
            ObjectMapper objectMapper
    ) {
        this.queryService = queryService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public ReviewAgentToolDefinition definition() {
        return new ReviewAgentToolDefinition(
                NAME,
                "读取当前固定revision的文件、包和Chunk数量摘要，用于判断模块边界；不返回完整源码。",
                Map.of(
                        "type", "object",
                        "properties", Map.of(),
                        "additionalProperties", false
                )
        );
    }

    @Override
    public AgentToolResult execute(ReviewAgentContext context, JsonNode arguments) {
        if (!arguments.isObject() || arguments.size() != 0) {
            throw new AgentToolExecutionException("analyzeProjectStructure不接受参数");
        }
        List<SourceDocumentResponse> documents = queryService.listDocuments(
                context.projectId(), context.revision(), properties.getMaxStructureDocuments()
        );
        List<DocumentSummary> summaries = documents.stream().map(this::toSummary).toList();
        return AgentToolResult.success(
                writeJson(new StructureOutput(context.revision(), summaries)),
                "documents=" + summaries.size(),
                null
        );
    }

    private DocumentSummary toSummary(SourceDocumentResponse document) {
        return new DocumentSummary(
                document.filePath(), document.sourceKind(), document.fileType(),
                document.packageName(), document.chunkCount()
        );
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new AgentToolExecutionException("序列化项目结构工具结果失败", exception);
        }
    }

    private record StructureOutput(String revision, List<DocumentSummary> documents) {
    }

    private record DocumentSummary(
            String filePath,
            String sourceKind,
            String fileType,
            String packageName,
            Integer chunkCount
    ) {
    }
}
