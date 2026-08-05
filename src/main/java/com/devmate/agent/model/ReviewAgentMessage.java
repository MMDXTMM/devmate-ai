package com.devmate.agent.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReviewAgentMessage(
        String role,
        String content,
        @JsonProperty("tool_call_id") String toolCallId,
        @JsonProperty("tool_calls") List<ReviewAgentToolCall> toolCalls
) {
    public ReviewAgentMessage {
        toolCalls = toolCalls == null || toolCalls.isEmpty() ? null : List.copyOf(toolCalls);
    }

    public static ReviewAgentMessage system(String content) {
        return new ReviewAgentMessage("system", content, null, null);
    }

    public static ReviewAgentMessage user(String content) {
        return new ReviewAgentMessage("user", content, null, null);
    }

    public static ReviewAgentMessage tool(String callId, String content) {
        return new ReviewAgentMessage("tool", content, callId, null);
    }
}
