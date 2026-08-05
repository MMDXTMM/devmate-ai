package com.devmate.tool.builtin;

import com.devmate.agent.config.ReviewAgentProperties;
import com.devmate.agent.model.ReviewAgentToolDefinition;
import com.devmate.review.dto.StaticAnalysisResponse;
import com.devmate.review.dto.StaticFindingResponse;
import com.devmate.review.service.StaticAnalysisStateService;
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
public class GetStaticAnalysisTool implements AgentTool {

    public static final String NAME = "getStaticAnalysis";

    private final StaticAnalysisStateService stateService;
    private final ReviewAgentProperties properties;
    private final ObjectMapper objectMapper;

    public GetStaticAnalysisTool(
            StaticAnalysisStateService stateService,
            ReviewAgentProperties properties,
            ObjectMapper objectMapper
    ) {
        this.stateService = stateService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public ReviewAgentToolDefinition definition() {
        return new ReviewAgentToolDefinition(
                NAME,
                "读取当前Diff已经完成的PMD与DevMate确定性静态分析结果。只读，不重新运行分析。",
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
            throw new AgentToolExecutionException("getStaticAnalysis不接受参数");
        }
        StaticAnalysisResponse response = stateService.getByTask(
                context.projectId(), context.staticAnalysisTaskId()
        );
        List<StaticFinding> findings = response.findings().stream()
                .limit(properties.getMaxStaticFindings())
                .map(this::toFinding)
                .toList();
        StaticOutput output = new StaticOutput(
                response.toolName(), response.toolVersion(), response.analyzedFiles(),
                response.findingCount(), response.findings().size() > findings.size(), findings
        );
        return AgentToolResult.success(
                writeJson(output),
                "analyzedFiles=" + response.analyzedFiles() + ";findings=" + findings.size(),
                null
        );
    }

    private StaticFinding toFinding(StaticFindingResponse finding) {
        return new StaticFinding(
                finding.ruleId(), finding.category(), finding.severity(), finding.filePath(),
                finding.startLine(), finding.endLine(), finding.message(), finding.evidence()
        );
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new AgentToolExecutionException("序列化静态分析工具结果失败", exception);
        }
    }

    private record StaticOutput(
            String toolName,
            String toolVersion,
            Integer analyzedFiles,
            Integer findingCount,
            boolean truncated,
            List<StaticFinding> findings
    ) {
    }

    private record StaticFinding(
            String ruleId,
            String category,
            String severity,
            String filePath,
            Integer startLine,
            Integer endLine,
            String message,
            String evidence
    ) {
    }
}
