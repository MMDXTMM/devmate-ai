package com.devmate.review.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devmate.knowledge.source.GitCloneResult;
import com.devmate.knowledge.source.GitSourceClient;
import com.devmate.project.dto.CreateProjectRequest;
import com.devmate.project.dto.ProjectResponse;
import com.devmate.project.service.ProjectService;
import com.devmate.review.entity.CodeReviewFile;
import com.devmate.review.entity.CodeReviewTask;
import com.devmate.review.mapper.CodeReviewFileMapper;
import com.devmate.review.mapper.CodeReviewTaskMapper;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "devmate.source.workspace-root=${java.io.tmpdir}/devmate-diff-tests")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReviewDiffControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ProjectService projectService;
    @Autowired
    private CodeReviewTaskMapper taskMapper;
    @Autowired
    private CodeReviewFileMapper fileMapper;

    @MockitoBean
    private GitSourceClient gitSourceClient;

    @Test
    void createsCoverageReportAndMapsChangedJavaLinesToSymbols() throws Exception {
        ProjectResponse project = createProject("diff-success");
        mockRepositoryWithTwoCommits();
        mockMvc.perform(post("/api/projects/{projectId}/imports", project.id()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/projects/{projectId}/review-diffs", project.id())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.changedFiles").value(3))
                .andExpect(jsonPath("$.data.fullyMappedFiles").value(2))
                .andExpect(jsonPath("$.data.skippedFiles").value(1))
                .andExpect(jsonPath("$.data.files.length()").value(3));

        CodeReviewTask task = taskMapper.selectOne(Wrappers.lambdaQuery(CodeReviewTask.class)
                .eq(CodeReviewTask::getProjectId, project.id())
                .last("LIMIT 1"));
        assertThat(task.getBaseRevision()).hasSize(40);
        assertThat(task.getTargetRevision()).hasSize(40);
        assertThat(fileMapper.selectCount(Wrappers.lambdaQuery(CodeReviewFile.class)
                .eq(CodeReviewFile::getReviewTaskId, task.getId()))).isEqualTo(3);
        CodeReviewFile appFile = fileMapper.selectOne(Wrappers.lambdaQuery(CodeReviewFile.class)
                .eq(CodeReviewFile::getReviewTaskId, task.getId())
                .eq(CodeReviewFile::getNewPath, "src/main/java/com/example/App.java")
                .last("LIMIT 1"));
        assertThat(appFile.getCoverageStatus()).isEqualTo("FULL");
        assertThat(appFile.getNewPathHash())
                .isEqualTo(sha256("src/main/java/com/example/App.java"));
        assertThat(appFile.getMappedSymbolsJson()).contains("com.example.App#run()");
        assertThat(appFile.getBaseChangedLinesJson()).isNotBlank();

        CodeReviewFile deletedFile = fileMapper.selectOne(Wrappers.lambdaQuery(CodeReviewFile.class)
                .eq(CodeReviewFile::getReviewTaskId, task.getId())
                .eq(CodeReviewFile::getOldPath, "src/main/java/com/example/Old.java")
                .last("LIMIT 1"));
        assertThat(deletedFile.getCoverageStatus()).isEqualTo("FULL");
        assertThat(deletedFile.getNewPath()).isNull();
        assertThat(deletedFile.getNewPathHash()).isNull();
        assertThat(deletedFile.getMappedSymbolsJson()).contains("BASE", "com.example.Old");

        mockMvc.perform(get("/api/projects/{projectId}/review-diffs/latest", project.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(task.getId().toString()))
                .andExpect(jsonPath("$.data.changedFiles").value(3));
    }

    @Test
    void rejectsDiffBeforeSourceImport() throws Exception {
        ProjectResponse project = createProject("diff-without-import");

        mockMvc.perform(post("/api/projects/{projectId}/review-diffs", project.id())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请先成功导入项目源码"));
    }

    @Test
    void validatesRequestedRevisionFormat() throws Exception {
        ProjectResponse project = createProject("invalid-diff-revision");

        mockMvc.perform(post("/api/projects/{projectId}/review-diffs", project.id())
                        .contentType("application/json")
                        .content("{\"baseRevision\":\"not-a-sha\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("基准版本必须是7到40位Git提交哈希"));
    }

    private ProjectResponse createProject(String name) {
        return projectService.createProject(new CreateProjectRequest(
                name,
                "Diff测试项目",
                "GIT",
                "https://github.com/example/" + name + ".git",
                "main"
        ));
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前JDK不支持SHA-256", exception);
        }
    }

    private void mockRepositoryWithTwoCommits() {
        given(gitSourceClient.cloneRepository(anyString(), eq("main"), any(Path.class)))
                .willAnswer(invocation -> {
                    Path target = invocation.getArgument(2);
                    try (Git git = Git.init().setDirectory(target.toFile()).call()) {
                        git.getRepository().getConfig().setString("user", null, "name", "DevMate Test");
                        git.getRepository().getConfig().setString("user", null, "email", "test@example.com");
                        git.getRepository().getConfig().save();
                        Path javaRoot = target.resolve("src/main/java/com/example");
                        Files.createDirectories(javaRoot);
                        Files.writeString(javaRoot.resolve("App.java"), """
                                package com.example;
                                class App {
                                    void run() {
                                    }
                                }
                                """);
                        Files.writeString(javaRoot.resolve("Old.java"), "package com.example; class Old {}\n");
                        git.add().addFilepattern(".").call();
                        git.commit().setMessage("base").call();

                        Files.writeString(javaRoot.resolve("App.java"), """
                                package com.example;
                                class App {
                                    void run() {
                                        System.out.println("new line");
                                    }
                                }
                                """);
                        Files.delete(javaRoot.resolve("Old.java"));
                        Files.writeString(target.resolve("application.yml"), "feature: true\n");
                        git.add().addFilepattern(".").call();
                        git.rm().addFilepattern("src/main/java/com/example/Old.java").call();
                        String revision = git.commit().setMessage("target").call().name();
                        return new GitCloneResult(target, revision);
                    }
                });
    }
}
