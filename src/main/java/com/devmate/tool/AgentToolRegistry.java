package com.devmate.tool;

import com.devmate.agent.model.ReviewAgentToolDefinition;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class AgentToolRegistry {

    private final Map<String, AgentTool> tools;

    public AgentToolRegistry(List<AgentTool> tools) {
        Map<String, AgentTool> indexed = new LinkedHashMap<>();
        for (AgentTool tool : tools) {
            String name = tool.definition().function().name();
            if (indexed.putIfAbsent(name, tool) != null) {
                throw new IllegalStateException("Agent工具名称重复：" + name);
            }
        }
        this.tools = Map.copyOf(indexed);
    }

    public List<ReviewAgentToolDefinition> definitions() {
        return tools.values().stream().map(AgentTool::definition).toList();
    }

    public Optional<AgentTool> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }
}
