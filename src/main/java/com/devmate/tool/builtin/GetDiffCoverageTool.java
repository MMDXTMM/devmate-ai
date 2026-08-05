package com.devmate.tool.builtin;

import com.devmate.agent.config.ReviewAgentProperties;
import com.devmate.agent.model.ReviewAgentToolDefinition;
import com.devmate.review.dto.MappedSymbolResponse;
import com.devmate.review.dto.ReviewDiffResponse;
import com.devmate.review.dto.ReviewFileResponse;
import com.devmate.review.service.ReviewDiffStateService;
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
public class GetDiffCoverageTool implements AgentTool {

    public static final String NAME = "getDiffCoverage";

    private final ReviewDiffStateService diffStateService;
    private final ReviewAgentProperties properties;
    private final ObjectMapper objectMapper;

    public GetDiffCoverageTool(
            ReviewDiffStateService diffStateService,
            ReviewAgentProperties properties,
            ObjectMapper objectMapper
    ) {
        this.diffStateService = diffStateService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public ReviewAgentToolDefinition definition() {
        return new ReviewAgentToolDefinition(
                NAME,
                "读取本次已固定Git Diff的覆盖清单、变更类型和映射符号。只读，不重新执行Git操作。",
                Map.of(
                        "type", "object",
                        "properties", Map.of(),
                        "additionalProperties", false
                )
        );
    }

    @Override
    public AgentToolResult execute(ReviewAgentContext context, JsonNode arguments) {
        requireEmpty(arguments);
        ReviewDiffResponse response = diffStateService.getByTask(
                context.projectId(), context.reviewTaskId()
        );
        List<DiffFile> files = response.files().stream()
                .limit(properties.getMaxDiffFiles())
                .map(this::toFile)
                .toList();
        DiffOutput output = new DiffOutput(
                response.baseRevision(),
                response.targetRevision(),
                response.changedFiles(),
                response.fullyMappedFiles(),
                response.partiallyMappedFiles(),
                response.skippedFiles(),
                response.files().size() > files.size(),
                files
        );
        return AgentToolResult.success(
                writeJson(output),
                "changedFiles=" + response.changedFiles() + ";returnedFiles=" + files.size(),
                null
        );
    }

    private DiffFile toFile(ReviewFileResponse file) {
        List<String> symbols = file.mappedSymbols().stream()
                .filter(symbol -> "TARGET".equals(symbol.revisionSide()))
                .map(MappedSymbolResponse::symbolName)
                .distinct()
                .limit(10)
                .toList();
        return new DiffFile(
                file.oldPath(), file.newPath(), file.changeType(), file.coverageStatus(),
                file.additions(), file.deletions(), symbols, file.skipReason()
        );
    }

    private void requireEmpty(JsonNode arguments) {
        if (!arguments.isObject() || arguments.size() != 0) {
            throw new AgentToolExecutionException("getDiffCoverage不接受参数");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new AgentToolExecutionException("序列化Diff工具结果失败", exception);
        }
    }

    private record DiffOutput(
            String baseRevision,
            String targetRevision,
            Integer changedFiles,
            Integer fullyMappedFiles,
            Integer partiallyMappedFiles,
            Integer skippedFiles,
            boolean truncated,
            List<DiffFile> files
    ) {
    }

    private record DiffFile(
            String oldPath,
            String newPath,
            String changeType,
            String coverageStatus,
            Integer additions,
            Integer deletions,
            List<String> mappedSymbols,
            String skipReason
    ) {
    }
}
