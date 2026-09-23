package com.devmate.tool.service;

import com.devmate.agent.config.ReviewAgentProperties;
import com.devmate.agent.model.ReviewAgentToolCall;
import com.devmate.agent.model.ReviewAgentToolDefinition;
import com.devmate.tool.AgentTool;
import com.devmate.tool.AgentToolRegistry;
import com.devmate.tool.model.AgentToolResult;
import com.devmate.tool.model.ReviewAgentContext;
import com.devmate.user.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class AgentToolExecutorTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void propagatesAuthenticationToTimedToolWorker() {
        AgentToolRegistry registry = mock(AgentToolRegistry.class);
        ToolCallAuditService auditService = mock(ToolCallAuditService.class);
        ReviewAgentProperties properties = new ReviewAgentProperties();
        AgentTool tool = new AuthenticationReadingTool();
        given(registry.find("authenticatedTool")).willReturn(Optional.of(tool));
        given(auditService.start(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).willReturn(9L);
        AuthenticatedUser user = new AuthenticatedUser(7L, "reviewer");
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user, null, java.util.List.of())
        );
        AgentToolExecutor executor = new AgentToolExecutor(
                registry, auditService, properties, new ObjectMapper()
        );

        AgentToolResult result = executor.execute(
                new ReviewAgentContext(1L, 2L, 3L, 4L, "revision"),
                new ReviewAgentToolCall("call-1", "function",
                        new ReviewAgentToolCall.FunctionCall("authenticatedTool", "{}")),
                1
        );

        assertThat(result.succeeded()).isTrue();
        assertThat(result.content()).isEqualTo("{\"userId\":\"7\"}");
    }

    private static class AuthenticationReadingTool implements AgentTool {

        @Override
        public ReviewAgentToolDefinition definition() {
            return new ReviewAgentToolDefinition("authenticatedTool", "test", Map.of());
        }

        @Override
        public AgentToolResult execute(ReviewAgentContext context, JsonNode arguments) {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            AuthenticatedUser user = (AuthenticatedUser) principal;
            return AgentToolResult.success(
                    "{\"userId\":\"" + user.id() + "\"}", "authenticated", null
            );
        }
    }
}
