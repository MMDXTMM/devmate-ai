package com.devmate.review.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devmate.agent.entity.AiInvocationLog;
import com.devmate.agent.mapper.AiInvocationLogMapper;
import com.devmate.agent.model.AiReviewFinding;
import com.devmate.agent.model.AiReviewModel;
import com.devmate.agent.model.AiReviewModelRegistry;
import com.devmate.agent.model.AiReviewModelResult;
import com.devmate.agent.model.ReviewAgentMessage;
import com.devmate.agent.model.ReviewAgentModel;
import com.devmate.agent.model.ReviewAgentModelRegistry;
import com.devmate.agent.model.ReviewAgentToolCall;
import com.devmate.agent.model.ReviewAgentTurn;
import com.devmate.knowledge.dto.RetrievalHitResponse;
import com.devmate.knowledge.dto.RetrievalSearchResponse;
import com.devmate.knowledge.entity.IndexTask;
import com.devmate.knowledge.entity.KnowledgeChunk;
import com.devmate.knowledge.entity.KnowledgeDocument;
import com.devmate.knowledge.mapper.IndexTaskMapper;
import com.devmate.knowledge.mapper.KnowledgeChunkMapper;
import com.devmate.knowledge.mapper.KnowledgeDocumentMapper;
import com.devmate.knowledge.retrieval.ContextRetrievalService;
import com.devmate.project.dto.CreateProjectRequest;
import com.devmate.project.dto.ProjectResponse;
import com.devmate.project.service.ProjectService;
import com.devmate.review.entity.AiReviewTask;
import com.devmate.review.entity.CodeReviewFile;
import com.devmate.review.entity.CodeReviewTask;
import com.devmate.review.entity.StaticAnalysisTask;
import com.devmate.review.mapper.AiReviewTaskMapper;
import com.devmate.review.mapper.CodeReviewFileMapper;
import com.devmate.review.mapper.CodeReviewTaskMapper;
import com.devmate.review.mapper.StaticAnalysisTaskMapper;
import com.devmate.tool.entity.ToolCallLog;
import com.devmate.tool.mapper.ToolCallLogMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgentAiReviewControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ProjectService projectService;
    @Autowired private IndexTaskMapper indexTaskMapper;
    @Autowired private KnowledgeDocumentMapper documentMapper;
    @Autowired private KnowledgeChunkMapper chunkMapper;
    @Autowired private CodeReviewTaskMapper reviewTaskMapper;
    @Autowired private CodeReviewFileMapper reviewFileMapper;
    @Autowired private StaticAnalysisTaskMapper staticTaskMapper;
    @Autowired private AiReviewTaskMapper aiReviewTaskMapper;
    @Autowired private AiInvocationLogMapper invocationMapper;
    @Autowired private ToolCallLogMapper toolCallLogMapper;

    @MockitoBean private AiReviewModelRegistry finalModelRegistry;
    @MockitoBean private ReviewAgentModelRegistry agentModelRegistry;
    @MockitoBean private ContextRetrievalService retrievalService;

    @Test
    void agentCallsControlledSearchAndReturnsAuditedReview() throws Exception {
        Fixture fixture = fixture("agent-success");
        AiReviewModel finalModel = finalModel(fixture.chunkId());
        ReviewAgentModel agentModel = mock(ReviewAgentModel.class);
        given(finalModelRegistry.current()).willReturn(finalModel);
        given(agentModelRegistry.current("TEST", "test-model")).willReturn(agentModel);
        given(retrievalService.search(any(), any())).willReturn(retrieval(fixture));
        given(agentModel.next(any(), any())).willReturn(
                toolTurn("call-search-1", "searchCode", "{\"query\":\"库存并发扣减风险\"}", 30),
                finalTurn(20)
        );

        mockMvc.perform(agentAiReviewPost(fixture))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.executionMode").value("AGENT"))
                .andExpect(jsonPath("$.data.promptVersion").value("review-agent-v1"))
                .andExpect(jsonPath("$.data.findingCount").value(1))
                .andExpect(jsonPath("$.data.totalTokens").value(200))
                .andExpect(jsonPath("$.data.toolCalls.length()").value(1))
                .andExpect(jsonPath("$.data.toolCalls[0].toolName").value("searchCode"))
                .andExpect(jsonPath("$.data.toolCalls[0].status").value("SUCCEEDED"));

        AiReviewTask task = latestTask(fixture.projectId());
        assertThat(task.getExecutionMode()).isEqualTo("AGENT");
        AiInvocationLog invocation = invocationMapper.selectById(task.getInvocationId());
        assertThat(invocation.getTotalTokens()).isEqualTo(200);
        ToolCallLog log = toolCallLogMapper.selectOne(Wrappers.lambdaQuery(ToolCallLog.class)
                .eq(ToolCallLog::getInvocationId, invocation.getId())
                .last("LIMIT 1"));
        assertThat(log.getArgumentsHash()).hasSize(64);
        assertThat(log.getArgumentsSummary()).contains("keys=query").doesNotContain("库存并发扣减风险");
        assertThat(log.getResultSummary()).contains("hits=1").doesNotContain("stock--");
    }

    @Test
    void rejectsWrongRevisionBeforeResolvingModelsOrCreatingAuditState() throws Exception {
        Fixture fixture = fixture("agent-wrong-revision");
        AiReviewModel finalModel = mock(AiReviewModel.class);
        ReviewAgentModel agentModel = mock(ReviewAgentModel.class);
        given(finalModelRegistry.current()).willReturn(finalModel);
        given(agentModelRegistry.current("TEST", "test-model")).willReturn(agentModel);

        mockMvc.perform(post("/api/projects/{projectId}/ai-reviews/agent", fixture.projectId())
                        .contentType(APPLICATION_JSON)
                        .content(aiReviewRequest(fixture.reviewTaskId(), "f".repeat(40))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40900))
                .andExpect(jsonPath("$.message").value("Diff已发生变化，请刷新后重试"));

        verify(finalModelRegistry, never()).current();
        verify(agentModelRegistry, never()).current();
        verify(agentModelRegistry, never()).current(any(), any());
        verify(finalModel, never()).review(any());
        verify(agentModel, never()).next(any(), any());
        assertThat(aiReviewTaskMapper.selectCount(Wrappers.lambdaQuery(AiReviewTask.class)
                .eq(AiReviewTask::getProjectId, fixture.projectId()))).isZero();
        assertThat(invocationMapper.selectCount(Wrappers.lambdaQuery(AiInvocationLog.class)
                .eq(AiInvocationLog::getProjectId, fixture.projectId()))).isZero();
    }

    @Test
    void agentFailsClosedWhenItStopsWithoutCodeEvidence() throws Exception {
        Fixture fixture = fixture("agent-without-evidence");
        AiReviewModel finalModel = finalModel(fixture.chunkId());
        given(finalModelRegistry.current()).willReturn(finalModel);
        ReviewAgentModel agentModel = mock(ReviewAgentModel.class);
        given(agentModelRegistry.current("TEST", "test-model")).willReturn(agentModel);
        given(agentModel.next(any(), any())).willReturn(finalTurn(10));

        mockMvc.perform(agentAiReviewPost(fixture))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Agent未获得可验证的代码检索证据"));

        AiReviewTask task = latestTask(fixture.projectId());
        assertThat(task.getStatus()).isEqualTo("FAILED");
        assertThat(task.getRunningKey()).isNull();
        assertThat(toolCallLogMapper.selectCount(Wrappers.lambdaQuery(ToolCallLog.class)
                .eq(ToolCallLog::getInvocationId, task.getInvocationId()))).isZero();
    }

    private ReviewAgentTurn toolTurn(
            String callId,
            String toolName,
            String arguments,
            int tokens
    ) {
        ReviewAgentToolCall call = new ReviewAgentToolCall(
                callId, "function", new ReviewAgentToolCall.FunctionCall(toolName, arguments)
        );
        return new ReviewAgentTurn(
                new ReviewAgentMessage("assistant", "", null, List.of(call)),
                tokens - 5, 5, tokens, "tool_calls"
        );
    }

    private ReviewAgentTurn finalTurn(int tokens) {
        return new ReviewAgentTurn(
                new ReviewAgentMessage("assistant", "取证完成", null, null),
                tokens - 5, 5, tokens, "stop"
        );
    }

    private AiReviewModel finalModel(Long chunkId) {
        AiReviewModel model = mock(AiReviewModel.class);
        given(model.providerName()).willReturn("TEST");
        given(model.modelName()).willReturn("test-model");
        given(model.review(any())).willReturn(new AiReviewModelResult(
                List.of(new AiReviewFinding(
                        String.valueOf(chunkId), "CONCURRENCY", "HIGH", "INFERENCE", 0.82,
                        "库存检查与扣减不是原子操作", "代码先检查stock再执行stock--",
                        "并发请求可能超卖", "使用条件更新", "运行并发测试"
                )),
                100, 50, 150, "stop"
        ));
        return model;
    }

    private Fixture fixture(String prefix) {
        String name = prefix + "-" + UUID.randomUUID();
        ProjectResponse project = projectService.createProject(new CreateProjectRequest(
                name, "Agent测试项目", "GIT",
                "https://github.com/example/" + name + ".git", "main"
        ));
        LocalDateTime now = LocalDateTime.now();
        String revision = "0123456789abcdef0123456789abcdef01234567";

        IndexTask indexTask = new IndexTask();
        indexTask.setProjectId(project.id());
        indexTask.setTaskType("FULL");
        indexTask.setRevision(revision);
        indexTask.setStatus("SUCCEEDED");
        indexTask.setTotalFiles(1);
        indexTask.setProcessedFiles(1);
        indexTask.setFailedFiles(0);
        indexTask.setCreatedAt(now);
        indexTask.setStartedAt(now);
        indexTask.setFinishedAt(now);
        indexTaskMapper.insert(indexTask);

        KnowledgeDocument document = new KnowledgeDocument();
        document.setProjectId(project.id());
        document.setSourceKind("SOURCE_CODE");
        document.setFileName("OrderService.java");
        document.setFilePath("src/main/java/OrderService.java");
        document.setPathHash("a".repeat(64));
        document.setFileType("JAVA");
        document.setContentHash("b".repeat(64));
        document.setRevision(revision);
        document.setStatus("PARSED");
        document.setChunkCount(1);
        document.setDeleted(0);
        document.setCreatedAt(now);
        document.setUpdatedAt(now);
        documentMapper.insert(document);

        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setProjectId(project.id());
        chunk.setDocumentId(document.getId());
        chunk.setChunkIndex(0);
        chunk.setChunkType("METHOD");
        chunk.setSymbolName("OrderService.reserve");
        chunk.setLanguage("JAVA");
        chunk.setContent("void reserve() { if (stock > 0) { stock--; } }");
        chunk.setContentHash("c".repeat(64));
        chunk.setTokenCount(30);
        chunk.setStartLine(20);
        chunk.setEndLine(32);
        chunk.setRevision(revision);
        chunk.setCreatedAt(now);
        chunkMapper.insert(chunk);

        CodeReviewTask reviewTask = new CodeReviewTask();
        reviewTask.setProjectId(project.id());
        reviewTask.setIndexTaskId(indexTask.getId());
        reviewTask.setBaseRevision("fedcba9876543210fedcba9876543210fedcba98");
        reviewTask.setTargetRevision(revision);
        reviewTask.setTriggerType("MANUAL");
        reviewTask.setStatus("SUCCEEDED");
        reviewTask.setChangedFiles(1);
        reviewTask.setFullyMappedFiles(1);
        reviewTask.setPartiallyMappedFiles(0);
        reviewTask.setSkippedFiles(0);
        reviewTask.setCreatedAt(now);
        reviewTask.setStartedAt(now);
        reviewTask.setFinishedAt(now);
        reviewTaskMapper.insert(reviewTask);

        CodeReviewFile reviewFile = new CodeReviewFile();
        reviewFile.setReviewTaskId(reviewTask.getId());
        reviewFile.setProjectId(project.id());
        reviewFile.setOldPath(document.getFilePath());
        reviewFile.setNewPath(document.getFilePath());
        reviewFile.setChangeType("MODIFY");
        reviewFile.setCoverageStatus("FULL");
        reviewFile.setAdditions(1);
        reviewFile.setDeletions(0);
        reviewFile.setChangedLinesJson("[{\"startLine\":24,\"endLine\":24}]");
        reviewFile.setMappedSymbolsJson("""
                [{"chunkId":%d,"revisionSide":"TARGET","chunkType":"METHOD",\
                "symbolName":"OrderService.reserve","startLine":20,"endLine":32}]
                """.formatted(chunk.getId()));
        reviewFile.setCreatedAt(now);
        reviewFileMapper.insert(reviewFile);

        StaticAnalysisTask staticTask = new StaticAnalysisTask();
        staticTask.setProjectId(project.id());
        staticTask.setReviewTaskId(reviewTask.getId());
        staticTask.setToolName("PMD+DEVMATE");
        staticTask.setToolVersion("7.26.0+v1");
        staticTask.setStatus("SUCCEEDED");
        staticTask.setAnalyzedFiles(1);
        staticTask.setFindingCount(0);
        staticTask.setCreatedAt(now);
        staticTask.setStartedAt(now);
        staticTask.setFinishedAt(now);
        staticTaskMapper.insert(staticTask);

        return new Fixture(
                project.id(), reviewTask.getId(), chunk.getId(), revision, document.getFilePath()
        );
    }

    private RetrievalSearchResponse retrieval(Fixture fixture) {
        RetrievalHitResponse hit = new RetrievalHitResponse(
                fixture.chunkId(), 1L, fixture.filePath(), "SOURCE_CODE", "METHOD",
                "OrderService.reserve", 20, 32, 0.92, 30,
                List.of("DIFF_SEED"), "void reserve() { if (stock > 0) { stock--; } }"
        );
        return new RetrievalSearchResponse(
                fixture.projectId(), fixture.revision(), "库存并发扣减风险", "lexical-graph-v1",
                "HYBRID", "LEXICAL_FALLBACK", "LOCAL", "local-hash-v1", false, 0,
                false, "NO_VECTOR_INDEX", 1, false, false, 6, 2500, 30, 1,
                0, 0, List.of(hit), List.of()
        );
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder agentAiReviewPost(
            Fixture fixture
    ) {
        return post("/api/projects/{projectId}/ai-reviews/agent", fixture.projectId())
                .contentType(APPLICATION_JSON)
                .content(aiReviewRequest(fixture.reviewTaskId(), fixture.revision()));
    }

    private String aiReviewRequest(Long reviewTaskId, String revision) {
        return """
                {"reviewTaskId":"%s","revision":"%s","attemptKey":"%s"}
                """.formatted(reviewTaskId, revision, UUID.randomUUID());
    }

    private AiReviewTask latestTask(Long projectId) {
        return aiReviewTaskMapper.selectOne(Wrappers.lambdaQuery(AiReviewTask.class)
                .eq(AiReviewTask::getProjectId, projectId)
                .orderByDesc(AiReviewTask::getCreatedAt)
                .last("LIMIT 1"));
    }

    private record Fixture(
            Long projectId,
            Long reviewTaskId,
            Long chunkId,
            String revision,
            String filePath
    ) {
    }
}
