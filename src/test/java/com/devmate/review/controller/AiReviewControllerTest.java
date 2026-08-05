package com.devmate.review.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devmate.agent.entity.AiInvocationLog;
import com.devmate.agent.mapper.AiInvocationLogMapper;
import com.devmate.agent.model.AiReviewException;
import com.devmate.agent.model.AiReviewFinding;
import com.devmate.agent.model.AiReviewModel;
import com.devmate.agent.model.AiReviewModelRegistry;
import com.devmate.agent.model.AiReviewModelResult;
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
import com.devmate.review.entity.CodeReviewFeedback;
import com.devmate.review.entity.CodeReviewFile;
import com.devmate.review.entity.CodeReviewTask;
import com.devmate.review.entity.ReviewFinding;
import com.devmate.review.entity.StaticAnalysisTask;
import com.devmate.review.mapper.AiReviewTaskMapper;
import com.devmate.review.mapper.CodeReviewFeedbackMapper;
import com.devmate.review.mapper.CodeReviewFileMapper;
import com.devmate.review.mapper.CodeReviewTaskMapper;
import com.devmate.review.mapper.ReviewFindingMapper;
import com.devmate.review.mapper.StaticAnalysisTaskMapper;
import com.devmate.review.service.AiReviewContext;
import com.devmate.review.service.AiReviewStateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AiReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ProjectService projectService;
    @Autowired
    private IndexTaskMapper indexTaskMapper;
    @Autowired
    private KnowledgeDocumentMapper documentMapper;
    @Autowired
    private KnowledgeChunkMapper chunkMapper;
    @Autowired
    private CodeReviewTaskMapper reviewTaskMapper;
    @Autowired
    private CodeReviewFileMapper reviewFileMapper;
    @Autowired
    private StaticAnalysisTaskMapper staticTaskMapper;
    @Autowired
    private AiReviewTaskMapper aiReviewTaskMapper;
    @Autowired
    private AiInvocationLogMapper invocationMapper;
    @Autowired
    private ReviewFindingMapper findingMapper;
    @Autowired
    private CodeReviewFeedbackMapper feedbackMapper;
    @Autowired
    private AiReviewStateService stateService;

    @MockitoBean
    private AiReviewModelRegistry modelRegistry;
    @MockitoBean
    private ContextRetrievalService retrievalService;

    @Test
    void createsEvidenceValidatedReviewAndPersistsAuditState() throws Exception {
        Fixture fixture = fixture("ai-review-success");
        AiReviewModel model = modelReturning(finding(String.valueOf(fixture.chunkId())));
        given(modelRegistry.current()).willReturn(model);
        given(retrievalService.search(any(), any())).willReturn(retrieval(fixture));

        mockMvc.perform(post("/api/projects/{projectId}/ai-reviews", fixture.projectId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.provider").value("TEST"))
                .andExpect(jsonPath("$.data.findingCount").value(1))
                .andExpect(jsonPath("$.data.rejectedFindings").value(0))
                .andExpect(jsonPath("$.data.totalTokens").value(150))
                .andExpect(jsonPath("$.data.findings[0].chunkId").value(fixture.chunkId().toString()))
                .andExpect(jsonPath("$.data.findings[0].filePath").value(fixture.filePath()))
                .andExpect(jsonPath("$.data.findings[0].startLine").value(20))
                .andExpect(jsonPath("$.data.findings[0].conclusionType").value("INFERENCE"));

        AiReviewTask task = latestTask(fixture.projectId());
        assertThat(task.getRunningKey()).isNull();
        assertThat(task.getRetrievalMode()).isEqualTo("LEXICAL_FALLBACK");
        AiInvocationLog invocation = invocationMapper.selectById(task.getInvocationId());
        assertThat(invocation.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(invocation.getPromptVersion()).isEqualTo("ai-review-v1");
        assertThat(invocation.getRequestHash()).hasSize(64);
        ReviewFinding persisted = findingMapper.selectOne(Wrappers.lambdaQuery(ReviewFinding.class)
                .eq(ReviewFinding::getAiReviewTaskId, task.getId())
                .last("LIMIT 1"));
        assertThat(persisted.getChunkId()).isEqualTo(fixture.chunkId());
        assertThat(persisted.getSource()).isEqualTo("LLM");

        mockMvc.perform(get("/api/projects/{projectId}/ai-reviews/latest", fixture.projectId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(task.getId().toString()))
                .andExpect(jsonPath("$.data.findings.length()").value(1));
    }

    @Test
    void rejectsInventedEvidenceWithoutPersistingFinding() throws Exception {
        Fixture fixture = fixture("ai-review-invented-chunk");
        AiReviewModel model = modelReturning(finding("999999"));
        given(modelRegistry.current()).willReturn(model);
        given(retrievalService.search(any(), any())).willReturn(retrieval(fixture));

        mockMvc.perform(post("/api/projects/{projectId}/ai-reviews", fixture.projectId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.findingCount").value(0))
                .andExpect(jsonPath("$.data.rejectedFindings").value(1));

        AiReviewTask task = latestTask(fixture.projectId());
        assertThat(findingMapper.selectCount(Wrappers.lambdaQuery(ReviewFinding.class)
                .eq(ReviewFinding::getAiReviewTaskId, task.getId()))).isZero();
    }

    @Test
    void recordsFailedTaskWhenModelCallFails() throws Exception {
        Fixture fixture = fixture("ai-review-provider-failure");
        AiReviewModel model = mock(AiReviewModel.class);
        given(model.providerName()).willReturn("TEST");
        given(model.modelName()).willReturn("test-model");
        given(model.review(any())).willThrow(new AiReviewException("AI审查模型调用失败"));
        given(modelRegistry.current()).willReturn(model);
        given(retrievalService.search(any(), any())).willReturn(retrieval(fixture));

        mockMvc.perform(post("/api/projects/{projectId}/ai-reviews", fixture.projectId()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("AI审查模型调用失败"));

        AiReviewTask task = latestTask(fixture.projectId());
        assertThat(task.getStatus()).isEqualTo("FAILED");
        assertThat(task.getRunningKey()).isNull();
        assertThat(task.getErrorMessage()).isEqualTo("AI审查模型调用失败");
        assertThat(invocationMapper.selectById(task.getInvocationId()).getStatus()).isEqualTo("FAILED");
    }

    @Test
    void requiresStaticAnalysisForCurrentDiff() throws Exception {
        ProjectResponse project = createProject("ai-review-without-diff");
        AiReviewModel model = mock(AiReviewModel.class);
        given(model.providerName()).willReturn("TEST");
        given(model.modelName()).willReturn("test-model");
        given(modelRegistry.current()).willReturn(model);

        mockMvc.perform(post("/api/projects/{projectId}/ai-reviews", project.id()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请先生成成功的Diff覆盖报告"));
    }

    @Test
    void databaseUniqueKeyPreventsConcurrentReviewForSameDiff() {
        Fixture fixture = fixture("ai-review-duplicate");
        AiReviewContext first = stateService.prepare(
                fixture.projectId(), "TEST", "test-model", "ai-review-v1"
        );

        assertThat(first.aiReviewTaskId()).isNotNull();
        assertThatThrownBy(() -> stateService.prepare(
                fixture.projectId(), "TEST", "test-model", "ai-review-v1"
        )).hasMessage("当前Diff已有AI审查任务正在运行");
    }

    @Test
    void staleRunningTaskIsFailedBeforeRetryStarts() {
        Fixture fixture = fixture("ai-review-stale-retry");
        AiReviewContext first = stateService.prepare(
                fixture.projectId(), "TEST", "test-model", "ai-review-v1"
        );
        AiReviewTask stale = aiReviewTaskMapper.selectById(first.aiReviewTaskId());
        stale.setStartedAt(LocalDateTime.now().minusMinutes(20));
        aiReviewTaskMapper.updateById(stale);

        AiReviewContext retried = stateService.prepare(
                fixture.projectId(), "TEST", "test-model", "ai-review-v1"
        );

        assertThat(retried.aiReviewTaskId()).isNotEqualTo(first.aiReviewTaskId());
        AiReviewTask expired = aiReviewTaskMapper.selectById(first.aiReviewTaskId());
        assertThat(expired.getStatus()).isEqualTo("FAILED");
        assertThat(expired.getRunningKey()).isNull();
        assertThat(invocationMapper.selectById(first.invocationId()).getErrorCode())
                .isEqualTo("STALE_TASK");
    }

    @Test
    void createsAndUpdatesFeedbackAndReturnsItWithLatestReview() throws Exception {
        Fixture fixture = fixture("ai-review-feedback");
        AiReviewModel model = modelReturning(finding(String.valueOf(fixture.chunkId())));
        given(modelRegistry.current()).willReturn(model);
        given(retrievalService.search(any(), any())).willReturn(retrieval(fixture));
        mockMvc.perform(post("/api/projects/{projectId}/ai-reviews", fixture.projectId()))
                .andExpect(status().isOk());
        ReviewFinding finding = latestFinding(fixture.projectId());

        mockMvc.perform(put(
                        "/api/projects/{projectId}/review-findings/{findingId}/feedback",
                        fixture.projectId(), finding.getId()
                ).contentType(APPLICATION_JSON)
                .content("""
                        {"feedbackType":"ACCEPTED","comment":"  已通过并发测试验证  "}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.findingId").value(finding.getId().toString()))
                .andExpect(jsonPath("$.data.feedbackType").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.comment").value("已通过并发测试验证"));

        CodeReviewFeedback created = feedbackMapper.selectOne(
                Wrappers.lambdaQuery(CodeReviewFeedback.class)
                        .eq(CodeReviewFeedback::getFindingId, finding.getId())
        );
        mockMvc.perform(put(
                        "/api/projects/{projectId}/review-findings/{findingId}/feedback",
                        fixture.projectId(), finding.getId()
                ).contentType(APPLICATION_JSON)
                .content("""
                        {"feedbackType":"FALSE_POSITIVE","comment":"当前调用方已持有同一把锁"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(created.getId().toString()))
                .andExpect(jsonPath("$.data.feedbackType").value("FALSE_POSITIVE"));

        assertThat(feedbackMapper.selectCount(
                Wrappers.lambdaQuery(CodeReviewFeedback.class)
                        .eq(CodeReviewFeedback::getFindingId, finding.getId())
        )).isEqualTo(1);
        mockMvc.perform(get("/api/projects/{projectId}/ai-reviews/latest", fixture.projectId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.findings[0].feedback.feedbackType")
                        .value("FALSE_POSITIVE"))
                .andExpect(jsonPath("$.data.findings[0].feedback.comment")
                        .value("当前调用方已持有同一把锁"));
    }

    @Test
    void rejectsFeedbackWhenFindingBelongsToAnotherProject() throws Exception {
        Fixture owner = fixture("ai-review-feedback-owner");
        Fixture other = fixture("ai-review-feedback-other");
        AiReviewModel model = modelReturning(finding(String.valueOf(owner.chunkId())));
        given(modelRegistry.current()).willReturn(model);
        given(retrievalService.search(any(), any())).willReturn(retrieval(owner));
        mockMvc.perform(post("/api/projects/{projectId}/ai-reviews", owner.projectId()))
                .andExpect(status().isOk());
        ReviewFinding finding = latestFinding(owner.projectId());

        mockMvc.perform(put(
                        "/api/projects/{projectId}/review-findings/{findingId}/feedback",
                        other.projectId(), finding.getId()
                ).contentType(APPLICATION_JSON)
                .content("""
                        {"feedbackType":"ACCEPTED"}
                        """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("审查结论不存在"));
    }

    @Test
    void rejectsInvalidFeedbackTypeAndOversizedComment() throws Exception {
        Fixture fixture = fixture("ai-review-feedback-validation");
        AiReviewModel model = modelReturning(finding(String.valueOf(fixture.chunkId())));
        given(modelRegistry.current()).willReturn(model);
        given(retrievalService.search(any(), any())).willReturn(retrieval(fixture));
        mockMvc.perform(post("/api/projects/{projectId}/ai-reviews", fixture.projectId()))
                .andExpect(status().isOk());
        ReviewFinding finding = latestFinding(fixture.projectId());

        mockMvc.perform(put(
                        "/api/projects/{projectId}/review-findings/{findingId}/feedback",
                        fixture.projectId(), finding.getId()
                ).contentType(APPLICATION_JSON)
                .content("""
                        {"feedbackType":"UNKNOWN"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请求体格式或枚举值不合法"));

        mockMvc.perform(put(
                        "/api/projects/{projectId}/review-findings/{findingId}/feedback",
                        fixture.projectId(), finding.getId()
                ).contentType(APPLICATION_JSON)
                .content("{\"feedbackType\":\"ACCEPTED\",\"comment\":\""
                        + "x".repeat(1001) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("反馈说明不能超过1000个字符"));
    }

    private Fixture fixture(String name) {
        ProjectResponse project = createProject(name);
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

        CodeReviewFile file = new CodeReviewFile();
        file.setReviewTaskId(reviewTask.getId());
        file.setProjectId(project.id());
        file.setOldPath(document.getFilePath());
        file.setNewPath(document.getFilePath());
        file.setChangeType("MODIFY");
        file.setCoverageStatus("FULL");
        file.setAdditions(1);
        file.setDeletions(0);
        file.setChangedLinesJson("[{\"startLine\":24,\"endLine\":24}]");
        file.setMappedSymbolsJson("""
                [{"chunkId":%d,"revisionSide":"TARGET","chunkType":"METHOD",\
                "symbolName":"OrderService.reserve","startLine":20,"endLine":32}]
                """.formatted(chunk.getId()));
        file.setCreatedAt(now);
        reviewFileMapper.insert(file);

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

        return new Fixture(project.id(), chunk.getId(), revision, document.getFilePath());
    }

    private ProjectResponse createProject(String name) {
        return projectService.createProject(new CreateProjectRequest(
                name,
                "AI审查测试项目",
                "GIT",
                "https://github.com/example/" + name + ".git",
                "main"
        ));
    }

    private AiReviewModel modelReturning(AiReviewFinding finding) {
        AiReviewModel model = mock(AiReviewModel.class);
        given(model.providerName()).willReturn("TEST");
        given(model.modelName()).willReturn("test-model");
        given(model.review(any())).willReturn(new AiReviewModelResult(
                List.of(finding), 100, 50, 150, "stop"
        ));
        return model;
    }

    private AiReviewFinding finding(String chunkId) {
        return new AiReviewFinding(
                chunkId, "CONCURRENCY", "HIGH", "INFERENCE", 0.82,
                "库存检查与扣减不是原子操作", "代码先检查stock再执行stock--",
                "两个请求同时读取到正库存时可能超卖", "使用数据库条件更新或原子缓存脚本",
                "运行并发测试并校验成功数和最终库存"
        );
    }

    private RetrievalSearchResponse retrieval(Fixture fixture) {
        RetrievalHitResponse hit = new RetrievalHitResponse(
                fixture.chunkId(), 1L, fixture.filePath(), "SOURCE_CODE", "METHOD",
                "OrderService.reserve", 20, 32, 0.92, 30,
                List.of("DIFF_SEED"), "void reserve() { if (stock > 0) { stock--; } }"
        );
        return new RetrievalSearchResponse(
                fixture.projectId(), fixture.revision(), "OrderService.reserve", "lexical-graph-v1",
                "HYBRID", "LEXICAL_FALLBACK", "LOCAL", "local-hash-v1", false, 0,
                false, "NO_VECTOR_INDEX", 1, false, false, 12, 6000, 30, 1,
                0, 0, List.of(hit), List.of()
        );
    }

    private AiReviewTask latestTask(Long projectId) {
        return aiReviewTaskMapper.selectOne(Wrappers.lambdaQuery(AiReviewTask.class)
                .eq(AiReviewTask::getProjectId, projectId)
                .orderByDesc(AiReviewTask::getCreatedAt)
                .last("LIMIT 1"));
    }

    private ReviewFinding latestFinding(Long projectId) {
        AiReviewTask task = latestTask(projectId);
        return findingMapper.selectOne(Wrappers.lambdaQuery(ReviewFinding.class)
                .eq(ReviewFinding::getAiReviewTaskId, task.getId())
                .last("LIMIT 1"));
    }

    private record Fixture(Long projectId, Long chunkId, String revision, String filePath) {
    }
}
