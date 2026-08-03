package com.devmate.project.controller;

import com.devmate.project.entity.Project;
import com.devmate.project.mapper.ProjectMapper;
import com.devmate.project.dto.CreateProjectRequest;
import com.devmate.project.dto.ProjectResponse;
import com.devmate.project.service.ProjectService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsGitProject() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "demo-service",
                                  "description": "用于代码审查的示例项目",
                                  "sourceType": "GIT",
                                  "sourceLocation": "https://github.com/example/demo-service.git",
                                  "defaultBranch": "main"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.name").value("demo-service"))
                .andExpect(jsonPath("$.data.sourceType").value("GIT"))
                .andExpect(jsonPath("$.data.status").value("CREATED"));

        List<Project> projects = projectMapper.selectList(null);
        assertThat(projects).hasSize(1);
        assertThat(projects.getFirst().getSourceLocation())
                .isEqualTo("https://github.com/example/demo-service.git");
    }

    @Test
    void rejectsBlankProjectName() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": " "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("项目名称不能为空"));
    }

    @Test
    void rejectsGitProjectWithoutRepositoryUrl() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "demo-service",
                                  "sourceType": "GIT"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("Git项目必须填写仓库地址"));
    }

    @Test
    void returnsProjectById() throws Exception {
        ProjectResponse created = projectService.createProject(new CreateProjectRequest(
                "query-demo",
                "详情查询测试项目",
                "GIT",
                "https://github.com/example/query-demo.git",
                "main"
        ));

        mockMvc.perform(get("/api/projects/{projectId}", created.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(created.id().toString()))
                .andExpect(jsonPath("$.data.name").value("query-demo"))
                .andExpect(jsonPath("$.data.status").value("CREATED"));
    }

    @Test
    void returnsNotFoundWhenProjectDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/projects/{projectId}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400))
                .andExpect(jsonPath("$.message").value("项目不存在"));
    }

    @Test
    void rejectsNonPositiveProjectId() throws Exception {
        mockMvc.perform(get("/api/projects/{projectId}", 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void returnsFilteredProjectPage() throws Exception {
        projectService.createProject(new CreateProjectRequest(
                "alpha-service", null, "LOCAL", "/tmp/alpha", "main"
        ));
        projectService.createProject(new CreateProjectRequest(
                "alpha-worker", null, "LOCAL", "/tmp/worker", "main"
        ));
        projectService.createProject(new CreateProjectRequest(
                "beta-service", null, "LOCAL", "/tmp/beta", "main"
        ));

        mockMvc.perform(get("/api/projects")
                        .param("page", "1")
                        .param("size", "1")
                        .param("name", "alpha")
                        .param("status", "CREATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.pages").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value(
                        org.hamcrest.Matchers.startsWith("alpha")
                ));
    }

    @Test
    void rejectsInvalidPageSize() throws Exception {
        mockMvc.perform(get("/api/projects").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("每页数量不能超过100"));
    }

    @Test
    void updatesProjectMetadata() throws Exception {
        ProjectResponse created = projectService.createProject(new CreateProjectRequest(
                "old-name", null, "LOCAL", "/tmp/old-name", "main"
        ));

        mockMvc.perform(put("/api/projects/{projectId}", created.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "new-name",
                                  "description": "更新后的项目",
                                  "sourceType": "GIT",
                                  "sourceLocation": "https://github.com/example/new-name.git",
                                  "defaultBranch": "develop"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(created.id().toString()))
                .andExpect(jsonPath("$.data.name").value("new-name"))
                .andExpect(jsonPath("$.data.sourceType").value("GIT"))
                .andExpect(jsonPath("$.data.defaultBranch").value("develop"))
                .andExpect(jsonPath("$.data.status").value("CREATED"));

        Project updated = projectMapper.selectById(created.id());
        assertThat(updated.getName()).isEqualTo("new-name");
        assertThat(updated.getStatus()).isEqualTo("CREATED");
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(created.updatedAt());
    }

    @Test
    void returnsNotFoundWhenUpdatingMissingProject() throws Exception {
        mockMvc.perform(put("/api/projects/{projectId}", 999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "missing-project",
                                  "sourceType": "LOCAL"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400))
                .andExpect(jsonPath("$.message").value("项目不存在"));
    }

    @Test
    void rejectsGitUpdateWithoutRepositoryUrl() throws Exception {
        ProjectResponse created = projectService.createProject(new CreateProjectRequest(
                "local-project", null, "LOCAL", "/tmp/local-project", "main"
        ));

        mockMvc.perform(put("/api/projects/{projectId}", created.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "git-project",
                                  "sourceType": "GIT"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("Git项目必须填写仓库地址"));

        Project unchanged = projectMapper.selectById(created.id());
        assertThat(unchanged.getName()).isEqualTo("local-project");
        assertThat(unchanged.getSourceType()).isEqualTo("LOCAL");
    }

    @Test
    void logicallyDeletesProjectAndHidesItFromQueries() throws Exception {
        ProjectResponse created = projectService.createProject(new CreateProjectRequest(
                "delete-demo", null, "LOCAL", "/tmp/delete-demo", "main"
        ));

        mockMvc.perform(delete("/api/projects/{projectId}", created.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").doesNotExist());

        assertThat(projectMapper.selectById(created.id())).isNull();
        Integer deleted = jdbcTemplate.queryForObject(
                "SELECT deleted FROM project WHERE id = ?",
                Integer.class,
                created.id()
        );
        assertThat(deleted).isEqualTo(1);

        mockMvc.perform(get("/api/projects/{projectId}", created.id()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));

        mockMvc.perform(get("/api/projects").param("name", "delete-demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void returnsNotFoundWhenDeletingProjectTwice() throws Exception {
        ProjectResponse created = projectService.createProject(new CreateProjectRequest(
                "delete-twice", null, "LOCAL", "/tmp/delete-twice", "main"
        ));

        mockMvc.perform(delete("/api/projects/{projectId}", created.id()))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/projects/{projectId}", created.id()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400))
                .andExpect(jsonPath("$.message").value("项目不存在"));
    }
}
