package com.devmate.review.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devmate.knowledge.source.GitCloneResult;
import com.devmate.knowledge.source.GitSourceClient;
import com.devmate.project.dto.CreateProjectRequest;
import com.devmate.project.dto.ProjectResponse;
import com.devmate.project.service.ProjectService;
import com.devmate.review.entity.ReviewFinding;
import com.devmate.review.entity.StaticAnalysisTask;
import com.devmate.review.mapper.ReviewFindingMapper;
import com.devmate.review.mapper.StaticAnalysisTaskMapper;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.hasItems;

@SpringBootTest(properties = "devmate.source.workspace-root=${java.io.tmpdir}/devmate-static-tests")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StaticAnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ProjectService projectService;
    @Autowired
    private StaticAnalysisTaskMapper taskMapper;
    @Autowired
    private ReviewFindingMapper findingMapper;

    @MockitoBean
    private GitSourceClient gitSourceClient;

    @Test
    void analyzesChangedJavaFilesAndPersistsDeterministicFindings() throws Exception {
        ProjectResponse project = createProject("static-success");
        mockRepositoryWithEmptyCatchBlock();
        mockMvc.perform(post("/api/projects/{projectId}/imports", project.id()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/projects/{projectId}/review-diffs", project.id())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/projects/{projectId}/static-analyses", project.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.toolName").value("PMD+DEVMATE"))
                .andExpect(jsonPath("$.data.analyzedFiles").value(1))
                .andExpect(jsonPath("$.data.findingCount").value(1))
                .andExpect(jsonPath("$.data.findings[0].ruleId").value("EmptyCatchBlock"))
                .andExpect(jsonPath("$.data.findings[0].source").value("STATIC"));

        StaticAnalysisTask task = taskMapper.selectOne(
                Wrappers.lambdaQuery(StaticAnalysisTask.class)
                        .eq(StaticAnalysisTask::getProjectId, project.id())
                        .last("LIMIT 1")
        );
        assertThat(task.getStatus()).isEqualTo("SUCCEEDED");
        ReviewFinding finding = findingMapper.selectOne(
                Wrappers.lambdaQuery(ReviewFinding.class)
                        .eq(ReviewFinding::getAnalysisTaskId, task.getId())
                        .last("LIMIT 1")
        );
        assertThat(finding.getFilePath()).isEqualTo("src/main/java/com/example/ReviewTarget.java");
        assertThat(finding.getPathHash()).hasSize(64);
        assertThat(finding.getFingerprint()).hasSize(64);

        mockMvc.perform(get("/api/projects/{projectId}/static-analyses/latest", project.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(task.getId().toString()))
                .andExpect(jsonPath("$.data.findingCount").value(1));
    }

    @Test
    void rejectsAnalysisBeforeDiffReportExists() throws Exception {
        ProjectResponse project = createProject("static-without-diff");

        mockMvc.perform(post("/api/projects/{projectId}/static-analyses", project.id()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请先生成成功的Diff覆盖报告"));
    }

    @Test
    void latestAnalysisUsesHigherIdForTiedTimestampAndKeepsFailedVisibility() throws Exception {
        ProjectResponse project = createProject("static-tied-latest");
        mockRepositoryWithEmptyCatchBlock();
        mockMvc.perform(post("/api/projects/{projectId}/imports", project.id()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/projects/{projectId}/review-diffs", project.id())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/projects/{projectId}/static-analyses", project.id()))
                .andExpect(status().isOk());

        StaticAnalysisTask first = taskMapper.selectOne(
                Wrappers.lambdaQuery(StaticAnalysisTask.class)
                        .eq(StaticAnalysisTask::getProjectId, project.id())
                        .orderByDesc(StaticAnalysisTask::getId)
                        .last("LIMIT 1")
        );
        LocalDateTime tiedTimestamp = LocalDateTime.of(2026, 8, 6, 12, 0);
        first.setCreatedAt(tiedTimestamp);
        taskMapper.updateById(first);

        StaticAnalysisTask latest = new StaticAnalysisTask();
        latest.setProjectId(project.id());
        latest.setReviewTaskId(first.getReviewTaskId());
        latest.setToolName(first.getToolName());
        latest.setToolVersion(first.getToolVersion());
        latest.setStatus("FAILED");
        latest.setAnalyzedFiles(0);
        latest.setFindingCount(0);
        latest.setErrorMessage("静态分析失败");
        latest.setCreatedAt(tiedTimestamp);
        latest.setStartedAt(tiedTimestamp);
        latest.setFinishedAt(tiedTimestamp);
        taskMapper.insert(latest);

        assertThat(latest.getId()).isGreaterThan(first.getId());
        mockMvc.perform(get("/api/projects/{projectId}/static-analyses/latest", project.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(latest.getId().toString()))
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.errorMessage").value("静态分析失败"));
    }

    @Test
    void detectsTransactionalSelfInvocationOnChangedCallLine() throws Exception {
        ProjectResponse project = createProject("transaction-self-call");
        mockRepositoryWithTransactionalSelfInvocation();
        mockMvc.perform(post("/api/projects/{projectId}/imports", project.id()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/projects/{projectId}/review-diffs", project.id())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/projects/{projectId}/static-analyses", project.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.findingCount").value(1))
                .andExpect(jsonPath("$.data.findings[0].ruleId").value("TransactionalSelfInvocation"))
                .andExpect(jsonPath("$.data.findings[0].category").value("TRANSACTION"))
                .andExpect(jsonPath("$.data.findings[0].severity").value("HIGH"));
    }

    @Test
    void detectsDataAccessInsideLoopAndSynchronizedContext() throws Exception {
        ProjectResponse project = createProject("data-access-context");
        mockRepositoryWithDataAccessUnderLock();
        mockMvc.perform(post("/api/projects/{projectId}/imports", project.id()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/projects/{projectId}/review-diffs", project.id())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/projects/{projectId}/static-analyses", project.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.findingCount").value(2))
                .andExpect(jsonPath("$.data.findings[*].ruleId").value(hasItems(
                        "DataAccessInsideLoop",
                        "BlockingDataAccessUnderLock"
                )));
    }

    private ProjectResponse createProject(String name) {
        return projectService.createProject(new CreateProjectRequest(
                name,
                "静态分析测试项目",
                "GIT",
                "https://github.com/example/" + name + ".git",
                "main"
        ));
    }

    private void mockRepositoryWithEmptyCatchBlock() {
        given(gitSourceClient.cloneRepository(anyString(), eq("main"), any(Path.class)))
                .willAnswer(invocation -> {
                    Path target = invocation.getArgument(2);
                    try (Git git = Git.init().setDirectory(target.toFile()).call()) {
                        git.getRepository().getConfig().setString("user", null, "name", "DevMate Test");
                        git.getRepository().getConfig().setString("user", null, "email", "test@example.com");
                        git.getRepository().getConfig().save();
                        Path javaRoot = target.resolve("src/main/java/com/example");
                        Files.createDirectories(javaRoot);
                        Path source = javaRoot.resolve("ReviewTarget.java");
                        Files.writeString(source, """
                                package com.example;
                                class ReviewTarget {
                                    void run() {
                                    }
                                }
                                """);
                        git.add().addFilepattern(".").call();
                        git.commit().setMessage("base").call();

                        Files.writeString(source, """
                                package com.example;
                                class ReviewTarget {
                                    void run() {
                                        try {
                                            System.out.println("work");
                                        } catch (RuntimeException exception) {
                                        }
                                    }
                                }
                                """);
                        git.add().addFilepattern(".").call();
                        String revision = git.commit().setMessage("add empty catch").call().name();
                        return new GitCloneResult(target, revision);
                    }
                });
    }

    private void mockRepositoryWithTransactionalSelfInvocation() {
        given(gitSourceClient.cloneRepository(anyString(), eq("main"), any(Path.class)))
                .willAnswer(invocation -> {
                    Path target = invocation.getArgument(2);
                    try (Git git = initializeRepository(target)) {
                        Path source = createJavaRoot(target).resolve("BillingService.java");
                        Files.writeString(source, """
                                package com.example;
                                import org.springframework.transaction.annotation.Transactional;
                                class BillingService {
                                    void execute() {}
                                    @Transactional void charge() {}
                                }
                                """);
                        commitAll(git, "base");
                        Files.writeString(source, """
                                package com.example;
                                import org.springframework.transaction.annotation.Transactional;
                                class BillingService {
                                    void execute() { charge(); }
                                    @Transactional void charge() {}
                                }
                                """);
                        return new GitCloneResult(target, commitAll(git, "add self invocation"));
                    }
                });
    }

    private void mockRepositoryWithDataAccessUnderLock() {
        given(gitSourceClient.cloneRepository(anyString(), eq("main"), any(Path.class)))
                .willAnswer(invocation -> {
                    Path target = invocation.getArgument(2);
                    try (Git git = initializeRepository(target)) {
                        Path source = createJavaRoot(target).resolve("BatchService.java");
                        Files.writeString(source, """
                                package com.example;
                                import java.util.List;
                                class BatchService {
                                    private OrderMapper orderMapper;
                                    synchronized void sync(List<Long> ids) {}
                                }
                                interface OrderMapper { Object selectById(Long id); }
                                """);
                        commitAll(git, "base");
                        Files.writeString(source, """
                                package com.example;
                                import java.util.List;
                                class BatchService {
                                    private OrderMapper orderMapper;
                                    synchronized void sync(List<Long> ids) {
                                        for (Long id : ids) {
                                            orderMapper.selectById(id);
                                        }
                                    }
                                }
                                interface OrderMapper { Object selectById(Long id); }
                                """);
                        return new GitCloneResult(target, commitAll(git, "add loop query"));
                    }
                });
    }

    private Git initializeRepository(Path target) throws Exception {
        Git git = Git.init().setDirectory(target.toFile()).call();
        git.getRepository().getConfig().setString("user", null, "name", "DevMate Test");
        git.getRepository().getConfig().setString("user", null, "email", "test@example.com");
        git.getRepository().getConfig().save();
        return git;
    }

    private Path createJavaRoot(Path target) throws Exception {
        Path javaRoot = target.resolve("src/main/java/com/example");
        Files.createDirectories(javaRoot);
        return javaRoot;
    }

    private String commitAll(Git git, String message) throws Exception {
        git.add().addFilepattern(".").call();
        return git.commit().setMessage(message).call().name();
    }
}
