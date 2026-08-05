package com.devmate.review.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devmate.agent.entity.AiInvocationLog;
import com.devmate.agent.mapper.AiInvocationLogMapper;
import com.devmate.knowledge.entity.IndexTask;
import com.devmate.knowledge.mapper.IndexTaskMapper;
import com.devmate.project.dto.CreateProjectRequest;
import com.devmate.project.dto.ProjectResponse;
import com.devmate.project.service.ProjectService;
import com.devmate.review.entity.AiReviewTask;
import com.devmate.review.entity.CodeReviewTask;
import com.devmate.review.entity.ReviewFinding;
import com.devmate.review.entity.StaticAnalysisTask;
import com.devmate.review.mapper.AiReviewTaskMapper;
import com.devmate.review.mapper.CodeReviewTaskMapper;
import com.devmate.review.mapper.ReviewEvaluationCaseMapper;
import com.devmate.review.mapper.ReviewEvaluationRunMapper;
import com.devmate.review.mapper.ReviewFindingMapper;
import com.devmate.review.mapper.StaticAnalysisTaskMapper;
import com.devmate.tool.entity.ToolCallLog;
import com.devmate.tool.mapper.ToolCallLogMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReviewEvaluationControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ProjectService projectService;
    @Autowired
    private IndexTaskMapper indexTaskMapper;
    @Autowired
    private CodeReviewTaskMapper reviewTaskMapper;
    @Autowired
    private StaticAnalysisTaskMapper staticTaskMapper;
    @Autowired
    private AiInvocationLogMapper invocationMapper;
    @Autowired
    private AiReviewTaskMapper aiReviewTaskMapper;
    @Autowired
    private ReviewFindingMapper findingMapper;
    @Autowired
    private ToolCallLogMapper toolCallLogMapper;
    @Autowired
    private ReviewEvaluationCaseMapper caseMapper;
    @Autowired
    private ReviewEvaluationRunMapper runMapper;

    @Test
    void createsAndListsDefectCasesWithProjectAndDatasetIsolation() throws Exception {
        Fixture fixture = fixture("evaluation-case", "FIXED");

        createDefectCase(
                fixture, "known-defects-v1", "concurrency-oversell",
                "CONCURRENCY", "src/OrderService.java", 20, 30
        ).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.targetRevision").value(fixture.revision()))
                .andExpect(jsonPath("$.data.expectationType").value("DEFECT"));

        mockMvc.perform(get("/api/projects/{projectId}/review-evaluation-cases", fixture.projectId())
                        .param("datasetVersion", "known-defects-v1")
                        .param("reviewTaskId", fixture.reviewTaskId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].caseKey").value("concurrency-oversell"));

        createDefectCase(
                fixture, "known-defects-v1", "concurrency-oversell",
                "CONCURRENCY", "src/OrderService.java", 20, 30
        ).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("同一评测集中的用例键不能重复"));

        ProjectResponse anotherProject = createProject("evaluation-case-other");
        mockMvc.perform(post("/api/projects/{projectId}/review-evaluation-cases", anotherProject.id())
                        .contentType(APPLICATION_JSON)
                        .content(defectCaseJson(
                                fixture.reviewTaskId(), "known-defects-v1", "cross-project",
                                "CONCURRENCY", "src/OrderService.java", 20, 30
                        )))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Diff任务不存在"));
    }

    @Test
    void rejectsContradictoryCleanCasesAndEscapingPaths() throws Exception {
        Fixture fixture = fixture("evaluation-validation", "FIXED");

        mockMvc.perform(post("/api/projects/{projectId}/review-evaluation-cases", fixture.projectId())
                        .contentType(APPLICATION_JSON)
                        .content(defectCaseJson(
                                fixture.reviewTaskId(), "known-defects-v1", "escaping",
                                "SECURITY", "../../secret.java", 1, 2
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("缺陷文件路径必须是项目内相对路径"));

        mockMvc.perform(post("/api/projects/{projectId}/review-evaluation-cases", fixture.projectId())
                        .contentType(APPLICATION_JSON)
                        .content(cleanCaseJson(fixture.reviewTaskId(), "clean-v1", "clean-control")))
                .andExpect(status().isOk());

        createDefectCase(
                fixture, "clean-v1", "unexpected-defect",
                "SQL", "src/OrderMapper.java", 10, 12
        ).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("同一Diff和数据集不能混用无缺陷与缺陷用例"));

        mockMvc.perform(post("/api/projects/{projectId}/review-evaluation-cases", fixture.projectId())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewTaskId":"%s",
                                  "datasetVersion":"invalid-clean",
                                  "caseKey":"clean-with-location",
                                  "name":"错误的无缺陷用例",
                                  "expectationType":"CLEAN",
                                  "category":"SQL",
                                  "filePath":"src/OrderMapper.java",
                                  "startLine":10,
                                  "endLine":12,
                                  "rationale":"不应允许位置"
                                }
                                """.formatted(fixture.reviewTaskId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("无缺陷用例不能填写缺陷类别、文件或行范围"));
    }

    @Test
    void evaluatesExistingAgentTaskAndReturnsIdempotentMetricsSnapshot() throws Exception {
        Fixture fixture = fixture("evaluation-run", "AGENT");
        createDefectCase(
                fixture, "known-defects-v1", "concurrency-oversell",
                "CONCURRENCY", "src/OrderService.java", 20, 30
        ).andExpect(status().isOk());
        createDefectCase(
                fixture, "known-defects-v1", "sql-n-plus-one",
                "SQL", "src/OrderMapper.java", 40, 45
        ).andExpect(status().isOk());
        insertFinding(
                fixture, "CONCURRENCY", "src/OrderService.java", 24, 26,
                "matching-concurrency"
        );
        insertFinding(
                fixture, "SECURITY", "src/AuthService.java", 8, 8,
                "unexpected-security"
        );
        insertToolCall(fixture, "call-1", "SUCCEEDED");
        insertToolCall(fixture, "call-2", "FAILED");

        String body = """
                {"datasetVersion":"known-defects-v1","aiReviewTaskId":"%s"}
                """.formatted(fixture.aiReviewTaskId());

        String firstId = mockMvc.perform(post(
                                "/api/projects/{projectId}/review-evaluation-runs",
                                fixture.projectId()
                        ).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.executionMode").value("AGENT"))
                .andExpect(jsonPath("$.data.expectedDefects").value(2))
                .andExpect(jsonPath("$.data.predictedFindings").value(2))
                .andExpect(jsonPath("$.data.truePositives").value(1))
                .andExpect(jsonPath("$.data.falsePositives").value(1))
                .andExpect(jsonPath("$.data.falseNegatives").value(1))
                .andExpect(jsonPath("$.data.manualReviewCount").value(0))
                .andExpect(jsonPath("$.data.partialMetrics").value(false))
                .andExpect(jsonPath("$.data.precision").value(0.500000))
                .andExpect(jsonPath("$.data.recall").value(0.500000))
                .andExpect(jsonPath("$.data.f1").value(0.500000))
                .andExpect(jsonPath("$.data.totalTokens").value(321))
                .andExpect(jsonPath("$.data.latencyMs").value(900))
                .andExpect(jsonPath("$.data.toolCallCount").value(2))
                .andExpect(jsonPath("$.data.toolSuccessCount").value(1))
                .andExpect(jsonPath("$.data.results.length()").value(3))
                .andReturn().getResponse().getContentAsString();

        String persistedId = objectId(firstId);
        mockMvc.perform(post("/api/projects/{projectId}/review-evaluation-runs", fixture.projectId())
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(persistedId));

        mockMvc.perform(get("/api/projects/{projectId}/review-evaluation-runs", fixture.projectId())
                        .param("datasetVersion", "known-defects-v1")
                        .param("reviewTaskId", fixture.reviewTaskId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(persistedId));

        createDefectCase(
                fixture, "known-defects-v1", "late-case",
                "PERFORMANCE", "src/LateService.java", 10, 12
        ).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "该评测集版本已经产生运行记录，请创建新版本后再增加用例"
                ));

        assertThat(runMapper.selectCount(Wrappers.lambdaQuery(
                com.devmate.review.entity.ReviewEvaluationRun.class
        ).eq(com.devmate.review.entity.ReviewEvaluationRun::getAiReviewTaskId,
                fixture.aiReviewTaskId()))).isEqualTo(1);
    }

    @Test
    void evaluatesCleanControlWithoutFindingsAsPass() throws Exception {
        Fixture fixture = fixture("evaluation-clean", "FIXED");
        mockMvc.perform(post("/api/projects/{projectId}/review-evaluation-cases", fixture.projectId())
                        .contentType(APPLICATION_JSON)
                        .content(cleanCaseJson(fixture.reviewTaskId(), "clean-v1", "clean-control")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/projects/{projectId}/review-evaluation-runs", fixture.projectId())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"datasetVersion":"clean-v1","aiReviewTaskId":"%s"}
                                """.formatted(fixture.aiReviewTaskId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.executionMode").value("FIXED"))
                .andExpect(jsonPath("$.data.expectedDefects").value(0))
                .andExpect(jsonPath("$.data.predictedFindings").value(0))
                .andExpect(jsonPath("$.data.precision").value(1.000000))
                .andExpect(jsonPath("$.data.recall").value(1.000000))
                .andExpect(jsonPath("$.data.f1").value(1.000000))
                .andExpect(jsonPath("$.data.results[0].outcome").value("CLEAN_PASS"));
    }

    private org.springframework.test.web.servlet.ResultActions createDefectCase(
            Fixture fixture,
            String datasetVersion,
            String caseKey,
            String category,
            String filePath,
            int startLine,
            int endLine
    ) throws Exception {
        return mockMvc.perform(post(
                        "/api/projects/{projectId}/review-evaluation-cases",
                        fixture.projectId()
                ).contentType(APPLICATION_JSON)
                .content(defectCaseJson(
                        fixture.reviewTaskId(), datasetVersion, caseKey,
                        category, filePath, startLine, endLine
                )));
    }

    private String defectCaseJson(
            Long reviewTaskId,
            String datasetVersion,
            String caseKey,
            String category,
            String filePath,
            int startLine,
            int endLine
    ) {
        return """
                {
                  "reviewTaskId":"%s",
                  "datasetVersion":"%s",
                  "caseKey":"%s",
                  "name":"%s",
                  "expectationType":"DEFECT",
                  "category":"%s",
                  "filePath":"%s",
                  "startLine":%d,
                  "endLine":%d,
                  "rationale":"人工确认的固定缺陷"
                }
                """.formatted(
                reviewTaskId, datasetVersion, caseKey, caseKey,
                category, filePath, startLine, endLine
        );
    }

    private String cleanCaseJson(Long reviewTaskId, String datasetVersion, String caseKey) {
        return """
                {
                  "reviewTaskId":"%s",
                  "datasetVersion":"%s",
                  "caseKey":"%s",
                  "name":"无缺陷对照",
                  "expectationType":"CLEAN",
                  "rationale":"人工审查确认该Diff没有目标类别缺陷"
                }
                """.formatted(reviewTaskId, datasetVersion, caseKey);
    }

    private Fixture fixture(String name, String executionMode) {
        ProjectResponse project = createProject(name);
        LocalDateTime now = LocalDateTime.now();
        String revision = "0123456789abcdef0123456789abcdef01234567";

        IndexTask indexTask = new IndexTask();
        indexTask.setProjectId(project.id());
        indexTask.setTaskType("FULL");
        indexTask.setRevision(revision);
        indexTask.setStatus("SUCCEEDED");
        indexTask.setTotalFiles(2);
        indexTask.setProcessedFiles(2);
        indexTask.setFailedFiles(0);
        indexTask.setCreatedAt(now);
        indexTask.setStartedAt(now);
        indexTask.setFinishedAt(now);
        indexTaskMapper.insert(indexTask);

        CodeReviewTask reviewTask = new CodeReviewTask();
        reviewTask.setProjectId(project.id());
        reviewTask.setIndexTaskId(indexTask.getId());
        reviewTask.setBaseRevision("fedcba9876543210fedcba9876543210fedcba98");
        reviewTask.setTargetRevision(revision);
        reviewTask.setTriggerType("MANUAL");
        reviewTask.setStatus("SUCCEEDED");
        reviewTask.setChangedFiles(2);
        reviewTask.setFullyMappedFiles(2);
        reviewTask.setPartiallyMappedFiles(0);
        reviewTask.setSkippedFiles(0);
        reviewTask.setCreatedAt(now);
        reviewTask.setStartedAt(now);
        reviewTask.setFinishedAt(now);
        reviewTaskMapper.insert(reviewTask);

        StaticAnalysisTask staticTask = new StaticAnalysisTask();
        staticTask.setProjectId(project.id());
        staticTask.setReviewTaskId(reviewTask.getId());
        staticTask.setToolName("PMD+DEVMATE");
        staticTask.setToolVersion("7.26.0+v1");
        staticTask.setStatus("SUCCEEDED");
        staticTask.setAnalyzedFiles(2);
        staticTask.setFindingCount(0);
        staticTask.setCreatedAt(now);
        staticTask.setStartedAt(now);
        staticTask.setFinishedAt(now);
        staticTaskMapper.insert(staticTask);

        AiInvocationLog invocation = new AiInvocationLog();
        invocation.setTraceId("trace-" + name);
        invocation.setProjectId(project.id());
        invocation.setProvider("TEST");
        invocation.setModelName("test-model");
        invocation.setRequestType("CODE_REVIEW");
        invocation.setStatus("SUCCEEDED");
        invocation.setPromptTokens(200);
        invocation.setCompletionTokens(121);
        invocation.setTotalTokens(321);
        invocation.setLatencyMs(900L);
        invocation.setPromptVersion("AGENT".equals(executionMode)
                ? "review-agent-v1" : "ai-review-v1");
        invocation.setRequestHash("a".repeat(64));
        invocation.setCreatedAt(now);
        invocationMapper.insert(invocation);

        AiReviewTask aiTask = new AiReviewTask();
        aiTask.setProjectId(project.id());
        aiTask.setReviewTaskId(reviewTask.getId());
        aiTask.setStaticAnalysisTaskId(staticTask.getId());
        aiTask.setInvocationId(invocation.getId());
        aiTask.setRevision(revision);
        aiTask.setProvider("TEST");
        aiTask.setModelName("test-model");
        aiTask.setPromptVersion(invocation.getPromptVersion());
        aiTask.setExecutionMode(executionMode);
        aiTask.setRetrievalConfigVersion("lexical-graph-v1");
        aiTask.setRetrievalMode("HYBRID");
        aiTask.setStatus("SUCCEEDED");
        aiTask.setContextChunks(4);
        aiTask.setFindingCount(0);
        aiTask.setRejectedFindings(0);
        aiTask.setCreatedAt(now);
        aiTask.setStartedAt(now);
        aiTask.setFinishedAt(now);
        aiReviewTaskMapper.insert(aiTask);

        return new Fixture(project.id(), reviewTask.getId(), staticTask.getId(),
                invocation.getId(), aiTask.getId(), revision);
    }

    private void insertFinding(
            Fixture fixture,
            String category,
            String filePath,
            int startLine,
            int endLine,
            String fingerprint
    ) {
        ReviewFinding value = new ReviewFinding();
        value.setProjectId(fixture.projectId());
        value.setReviewTaskId(fixture.reviewTaskId());
        value.setAnalysisTaskId(fixture.staticTaskId());
        value.setAiReviewTaskId(fixture.aiReviewTaskId());
        value.setSource("LLM");
        value.setRuleId("AI_" + category);
        value.setCategory(category);
        value.setSeverity("HIGH");
        value.setFilePath(filePath);
        value.setPathHash("b".repeat(64));
        value.setStartLine(startLine);
        value.setEndLine(endLine);
        value.setMessage("评测Finding");
        value.setEvidence("受控评测证据");
        value.setConclusionType("INFERENCE");
        value.setConfidence(new BigDecimal("0.8000"));
        value.setRiskScenario("评测风险场景");
        value.setSuggestion("评测修改建议");
        value.setVerification("评测验证方法");
        value.setFingerprint(fingerprint);
        value.setCreatedAt(LocalDateTime.now());
        findingMapper.insert(value);
    }

    private void insertToolCall(Fixture fixture, String callId, String status) {
        ToolCallLog value = new ToolCallLog();
        value.setInvocationId(fixture.invocationId());
        value.setProjectId(fixture.projectId());
        value.setToolCallId(callId);
        value.setStepNo("call-1".equals(callId) ? 1 : 2);
        value.setToolName("searchCode");
        value.setArgumentsHash("c".repeat(64));
        value.setArgumentsSummary("keys=query");
        value.setResultSummary("hits=1");
        value.setStatus(status);
        value.setLatencyMs(10L);
        value.setCreatedAt(LocalDateTime.now());
        toolCallLogMapper.insert(value);
    }

    private ProjectResponse createProject(String name) {
        return projectService.createProject(new CreateProjectRequest(
                name,
                "代码审查评测项目",
                "GIT",
                "https://github.com/example/" + name + ".git",
                "main"
        ));
    }

    private String objectId(String response) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(response).path("data").path("id").asText();
    }

    private record Fixture(
            Long projectId,
            Long reviewTaskId,
            Long staticTaskId,
            Long invocationId,
            Long aiReviewTaskId,
            String revision
    ) {
    }
}
