package com.devmate.agent.service;

import com.devmate.agent.config.ReviewAgentProperties;
import com.devmate.agent.model.AiReviewException;
import com.devmate.agent.model.ReviewAgentMessage;
import com.devmate.agent.model.ReviewAgentModel;
import com.devmate.agent.model.ReviewAgentModelRegistry;
import com.devmate.agent.model.ReviewAgentToolCall;
import com.devmate.agent.model.ReviewAgentTurn;
import com.devmate.knowledge.dto.RetrievalHitResponse;
import com.devmate.knowledge.dto.RetrievalSearchResponse;
import com.devmate.review.entity.CodeReviewTask;
import com.devmate.review.service.AiReviewContext;
import com.devmate.tool.AgentToolRegistry;
import com.devmate.tool.model.AgentToolResult;
import com.devmate.tool.service.AgentToolExecutor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ReviewAgentOrchestratorTest {

    @Test
    void completesWithCollectedEvidenceWhenToolBudgetIsReached() {
        ReviewAgentModelRegistry modelRegistry = mock(ReviewAgentModelRegistry.class);
        AgentToolRegistry toolRegistry = mock(AgentToolRegistry.class);
        AgentToolExecutor executor = mock(AgentToolExecutor.class);
        ReviewAgentModel model = mock(ReviewAgentModel.class);
        ReviewAgentProperties properties = new ReviewAgentProperties();
        properties.setMaxToolCalls(1);
        given(modelRegistry.current("TEST", "test-model")).willReturn(model);
        given(toolRegistry.definitions()).willReturn(List.of());
        ReviewAgentToolCall call = new ReviewAgentToolCall(
                "call-1", "function",
                new ReviewAgentToolCall.FunctionCall("searchCode", "{\"query\":\"库存并发\"}")
        );
        given(model.next(any(), any())).willReturn(new ReviewAgentTurn(
                new ReviewAgentMessage("assistant", "", null, List.of(call)),
                2, 3, 5, "tool_calls"
        ));
        given(executor.signature(call)).willReturn("search-signature");
        given(executor.execute(any(), any(), any(Integer.class)))
                .willReturn(AgentToolResult.success("{}", "hits=1", retrieval()));

        ReviewAgentOrchestrator orchestrator = new ReviewAgentOrchestrator(
                modelRegistry, toolRegistry, executor, properties
        );

        var result = orchestrator.research(context());

        assertThat(result.toolCallCount()).isEqualTo(1);
        assertThat(result.retrieval().hits()).hasSize(1);
        verify(model, times(1)).next(any(), any());
    }

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

    private RetrievalSearchResponse retrieval() {
        RetrievalHitResponse hit = new RetrievalHitResponse(
                11L, 12L, "src/main/java/InventoryService.java", "SOURCE", "METHOD",
                "reserve", 10, 20, 0.9, 20, List.of("SEMANTIC"), "void reserve() {}"
        );
        return new RetrievalSearchResponse(
                1L, "0123456789abcdef0123456789abcdef01234567", "库存并发", "test-v1",
                "HYBRID", "HYBRID", "LOCAL", "test", true, 1, false, null,
                1, false, false, 1, 100, 20, 1, 0, 0, List.of(hit), List.of()
        );
    }
}
