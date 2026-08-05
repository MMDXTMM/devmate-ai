package com.devmate.tool.builtin;

import com.devmate.agent.config.ReviewAgentProperties;
import com.devmate.agent.model.ReviewAgentToolDefinition;
import com.devmate.knowledge.dto.RetrievalHitResponse;
import com.devmate.knowledge.dto.RetrievalSearchResponse;
import com.devmate.knowledge.retrieval.ContextRetrievalService;
import com.devmate.knowledge.retrieval.RetrievalMode;
import com.devmate.knowledge.retrieval.RetrievalSearchCommand;
import com.devmate.tool.AgentTool;
import com.devmate.tool.model.AgentToolExecutionException;
import com.devmate.tool.model.AgentToolResult;
import com.devmate.tool.model.ReviewAgentContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class SearchCodeTool implements AgentTool {

    public static final String NAME = "searchCode";
    private static final Set<String> ALLOWED_ARGUMENTS = Set.of("query", "maxResults");

    private final ContextRetrievalService retrievalService;
    private final ReviewAgentProperties properties;
    private final ObjectMapper objectMapper;

    public SearchCodeTool(
            ContextRetrievalService retrievalService,
            ReviewAgentProperties properties,
            ObjectMapper objectMapper
    ) {
        this.retrievalService = retrievalService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public ReviewAgentToolDefinition definition() {
        return new ReviewAgentToolDefinition(
                NAME,
                "按语义与调用关系检索当前项目固定revision的代码、配置和数据库结构。用于获得可引用Chunk证据。",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "query", Map.of(
                                        "type", "string",
                                        "description", "要调查的具体风险、符号或调用关系，3到500个字符",
                                        "minLength", 3,
                                        "maxLength", 500
                                ),
                                "maxResults", Map.of(
                                        "type", "integer",
                                        "description", "返回证据数量，1到8",
                                        "minimum", 1,
                                        "maximum", 8
                                )
                        ),
                        "required", List.of("query"),
                        "additionalProperties", false
                )
        );
    }

    @Override
    public AgentToolResult execute(ReviewAgentContext context, JsonNode arguments) {
        validateFields(arguments);
        String query = arguments.path("query").asText("").trim();
        if (!StringUtils.hasText(query) || query.length() < 3 || query.length() > 500) {
            throw new AgentToolExecutionException("searchCode.query长度必须在3到500之间");
        }
        int maxResults = arguments.has("maxResults")
                ? arguments.path("maxResults").asInt(-1)
                : properties.getSearchTopK();
        if (maxResults < 1 || maxResults > 8) {
            throw new AgentToolExecutionException("searchCode.maxResults必须在1到8之间");
        }
        RetrievalSearchResponse response = retrievalService.search(
                context.projectId(),
                new RetrievalSearchCommand(
                        query,
                        context.revision(),
                        List.of(),
                        Math.min(maxResults, properties.getSearchTopK()),
                        properties.getSearchTokenBudget(),
                        RetrievalMode.HYBRID
                )
        );
        SearchOutput output = new SearchOutput(
                response.revision(), response.executedMode(), response.degradationReason(),
                response.usedTokens(), response.hits().stream().map(this::toEvidence).toList()
        );
        return AgentToolResult.success(
                writeJson(output),
                "mode=" + response.executedMode() + ";hits=" + response.hits().size()
                        + ";tokens=" + response.usedTokens(),
                response
        );
    }

    private Evidence toEvidence(RetrievalHitResponse hit) {
        return new Evidence(
                String.valueOf(hit.chunkId()), hit.filePath(), hit.chunkType(), hit.symbolName(),
                hit.startLine(), hit.endLine(), hit.reasons(), hit.excerpt()
        );
    }

    private void validateFields(JsonNode arguments) {
        if (!arguments.isObject()) {
            throw new AgentToolExecutionException("searchCode参数必须是JSON对象");
        }
        arguments.fieldNames().forEachRemaining(field -> {
            if (!ALLOWED_ARGUMENTS.contains(field)) {
                throw new AgentToolExecutionException("searchCode包含未授权参数：" + field);
            }
        });
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new AgentToolExecutionException("序列化代码检索工具结果失败", exception);
        }
    }

    private record SearchOutput(
            String revision,
            String executedMode,
            String degradationReason,
            int usedTokens,
            List<Evidence> evidence
    ) {
    }

    private record Evidence(
            String chunkId,
            String filePath,
            String chunkType,
            String symbolName,
            Integer startLine,
            Integer endLine,
            List<String> reasons,
            String excerpt
    ) {
    }
}
