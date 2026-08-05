package com.devmate.tool;

import com.devmate.agent.model.ReviewAgentToolDefinition;
import com.devmate.tool.model.AgentToolResult;
import com.devmate.tool.model.ReviewAgentContext;
import com.fasterxml.jackson.databind.JsonNode;

public interface AgentTool {

    ReviewAgentToolDefinition definition();

    AgentToolResult execute(ReviewAgentContext context, JsonNode arguments);
}
