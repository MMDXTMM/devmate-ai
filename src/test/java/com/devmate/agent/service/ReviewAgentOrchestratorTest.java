package com.devmate.agent.service;

import com.devmate.agent.config.ReviewAgentProperties;
import com.devmate.agent.model.AiReviewException;
import com.devmate.agent.model.ReviewAgentMessage;
import com.devmate.agent.model.ReviewAgentModel;
import com.devmate.agent.model.ReviewAgentModelRegistry;
import com.devmate.agent.model.ReviewAgentToolCall;
import com.devmate.agent.model.ReviewAgentTurn;
import com.devmate.review.entity.CodeReviewTask;
import com.devmate.review.service.AiReviewContext;
import com.devmate.tool.AgentToolRegistry;
import com.devmate.tool.model.AgentToolResult;
import com.devmate.tool.service.AgentToolExecutor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ReviewAgentOrchestratorTest {

    @Test
    void stopsBeforeExecutingTheThirdIdenticalToolCall() {
        ReviewAgentModelRegistry modelRegistry = mock(ReviewAgentModelRegistry.class);
        AgentToolRegistry toolRegistry = mock(AgentToolRegistry.class);
        AgentToolExecutor executor = mock(AgentToolExecutor.class);
        ReviewAgentModel model = mock(ReviewAgentModel.class);
        ReviewAgentProperties properties = new ReviewAgentProperties();
        given(modelRegistry.current("TEST", "test-model")).willReturn(model);
        given(toolRegistry.definitions()).willReturn(List.of());
        ReviewAgentToolCall call = new ReviewAgentToolCall(
                "call-1", "function",
                new ReviewAgentToolCall.FunctionCall("searchCode", "{\"query\":\"库存并发\"}")
        );
        ReviewAgentTurn toolTurn = new ReviewAgentTurn(
                new ReviewAgentMessage("assistant", "", null, List.of(call)),
                1, 1, 2, "tool_calls"
        );
        given(model.next(any(), any())).willReturn(toolTurn, toolTurn, toolTurn);
        given(executor.signature(call)).willReturn("same-signature");
        given(executor.execute(any(), any(), any(Integer.class)))
                .willReturn(AgentToolResult.success("{}", "ok", null));

        ReviewAgentOrchestrator orchestrator = new ReviewAgentOrchestrator(
                modelRegistry, toolRegistry, executor, properties
        );

        assertThatThrownBy(() -> orchestrator.research(context()))
                .isInstanceOf(AiReviewException.class)
                .hasMessage("Agent重复调用同一工具超过上限");
        verify(executor, times(2)).execute(any(), any(), any(Integer.class));
    }

    private AiReviewContext context() {
        CodeReviewTask reviewTask = new CodeReviewTask();
        reviewTask.setId(3L);
        reviewTask.setTargetRevision("0123456789abcdef0123456789abcdef01234567");
        return new AiReviewContext(1L, 2L, 4L, 5L, "TEST", "test-model", reviewTask, List.of());
    }
}
