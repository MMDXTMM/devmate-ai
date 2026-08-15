package com.devmate.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.devmate.agent.model.AiReviewModel;
import com.devmate.agent.model.AiReviewModelRegistry;
import com.devmate.agent.model.AiReviewModelResult;
import com.devmate.agent.model.ReviewAgentMessage;
import com.devmate.agent.model.ReviewAgentModel;
import com.devmate.agent.model.ReviewAgentModelRegistry;
import com.devmate.agent.model.ReviewAgentToolCall;
import com.devmate.agent.model.ReviewAgentTurn;
import com.devmate.knowledge.source.GitCloneResult;
import com.devmate.knowledge.source.GitSourceClient;
import com.devmate.project.dto.CreateProjectRequest;
import com.devmate.project.dto.ProjectResponse;
import com.devmate.project.service.ProjectService;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThan;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "devmate.source.workspace-root=${java.io.tmpdir}/devmate-workflow-tests")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BasicReviewWorkflowTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ProjectService projectService;

    @MockitoBean
    private GitSourceClient gitSourceClient;
    @MockitoBean
    private AiReviewModelRegistry modelRegistry;
    @MockitoBean
    private ReviewAgentModelRegistry agentModelRegistry;

    @Test
    void completesProjectToEvidenceGroundedReviewWorkflow() throws Exception {
        ProjectResponse project = projectService.createProject(new CreateProjectRequest(
                "workflow-demo",
                "基础代码审查闭环",
                "GIT",
                "https://github.com/example/workflow-demo.git",
                "main"
        ));
        mockRepository();
        mockAiModel();

        mockMvc.perform(post("/api/projects/{projectId}/imports", project.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.processedFiles").value(1));

        MvcResult diffResult = mockMvc.perform(post(
                        "/api/projects/{projectId}/review-diffs",
                        project.id()
                ).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.changedFiles").value(1))
                .andReturn();
        JsonNode diff = objectMapper.readTree(diffResult.getResponse().getContentAsString()).path("data");

        mockMvc.perform(post("/api/projects/{projectId}/static-analyses", project.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.findingCount").value(1));

        mockMvc.perform(post("/api/projects/{projectId}/embeddings/index", project.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.processedChunks", greaterThan(0)));

        String request = objectMapper.writeValueAsString(new ReviewRequest(
                diff.path("id").asLong(),
                diff.path("targetRevision").asText(),
                UUID.randomUUID().toString()
        ));
        mockMvc.perform(post("/api/projects/{projectId}/ai-reviews", project.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.executionMode").value("FIXED"))
                .andExpect(jsonPath("$.data.contextChunks", greaterThan(0)))
                .andExpect(jsonPath("$.data.findingCount").value(0));

        mockMvc.perform(get("/api/projects/{projectId}/ai-reviews/latest", project.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.totalTokens").value(60));
    }

    @Test
    void completesOneClickAgentReviewWorkflow() throws Exception {
        ProjectResponse project = projectService.createProject(new CreateProjectRequest(
                "workflow-agent-demo",
                "一键Agent代码审查闭环",
                "GIT",
                "https://github.com/example/workflow-agent-demo.git",
                "main"
        ));
        mockRepository();
        mockAiModel();
        mockAgentModel();

        String request = objectMapper.writeValueAsString(
                new WorkflowRequest(UUID.randomUUID().toString())
        );
        mockMvc.perform(post("/api/projects/{projectId}/review-workflows", project.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.currentStage").value("COMPLETED"))
                .andExpect(jsonPath("$.data.sourceImport.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.reviewDiff.changedFiles").value(1))
                .andExpect(jsonPath("$.data.staticAnalysis.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.embeddingIndex.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.aiReview.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.aiReview.executionMode").value("AGENT"))
                .andExpect(jsonPath("$.data.aiReview.toolCalls[0].toolName").value("searchCode"));
    }

    private void mockRepository() {
        given(gitSourceClient.cloneRepository(anyString(), eq("main"), any(Path.class)))
                .willAnswer(invocation -> {
                    Path target = invocation.getArgument(2);
                    try (Git git = Git.init().setDirectory(target.toFile()).call()) {
                        git.getRepository().getConfig().setString("user", null, "name", "DevMate Test");
                        git.getRepository().getConfig().setString("user", null, "email", "test@example.com");
                        git.getRepository().getConfig().save();
                        Path source = target.resolve("src/main/java/com/example/ReviewService.java");
                        Files.createDirectories(source.getParent());
                        Files.writeString(source, """
                                package com.example;
                                class ReviewService { void review() {} }
                                """);
                        git.add().addFilepattern(".").call();
                        git.commit().setMessage("base").call();

                        Files.writeString(source, """
                                package com.example;
                                class ReviewService {
                                    void review() {
                                        try { System.out.println("review"); }
                                        catch (RuntimeException exception) { }
                                    }
                                }
                                """);
                        git.add().addFilepattern(".").call();
                        String revision = git.commit().setMessage("add review logic").call().name();
                        return new GitCloneResult(target, revision);
                    }
                });
    }

    private void mockAiModel() {
        AiReviewModel model = mock(AiReviewModel.class);
        given(model.providerName()).willReturn("TEST");
        given(model.modelName()).willReturn("test-model");
        given(model.review(any())).willReturn(new AiReviewModelResult(
                List.of(), 40, 20, 60, "stop"
        ));
        given(modelRegistry.current()).willReturn(model);
    }

    private void mockAgentModel() {
        ReviewAgentModel model = mock(ReviewAgentModel.class);
        ReviewAgentToolCall call = new ReviewAgentToolCall(
                "call-search-1",
                "function",
                new ReviewAgentToolCall.FunctionCall("searchCode", "{\"query\":\"review\"}")
        );
        given(model.next(any(), any())).willReturn(
                new ReviewAgentTurn(
                        new ReviewAgentMessage("assistant", "", null, List.of(call)),
                        20, 5, 25, "tool_calls"
                ),
                new ReviewAgentTurn(
                        new ReviewAgentMessage("assistant", "取证完成", null, null),
                        10, 5, 15, "stop"
                )
        );
        given(agentModelRegistry.current()).willReturn(model);
    }

    private record ReviewRequest(Long reviewTaskId, String revision, String attemptKey) {
    }

    private record WorkflowRequest(String attemptKey) {
    }
}
