package com.devmate.review.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devmate.common.error.BusinessException;
import com.devmate.common.error.ErrorCode;
import com.devmate.knowledge.service.SourceImportService;
import com.devmate.project.dto.CreateProjectRequest;
import com.devmate.project.dto.ProjectResponse;
import com.devmate.project.service.ProjectService;
import com.devmate.review.entity.ReviewWorkflowRun;
import com.devmate.review.mapper.ReviewWorkflowRunMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReviewWorkflowControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ProjectService projectService;
    @Autowired private ReviewWorkflowRunMapper workflowRunMapper;

    @MockitoBean private SourceImportService sourceImportService;

    @Test
    void persistsReadableFailureAndReusesAttemptWithoutRepeatingWork() throws Exception {
        ProjectResponse project = createProject("workflow-failure");
        String attemptKey = UUID.randomUUID().toString();
        given(sourceImportService.importSource(project.id())).willThrow(
                new BusinessException(ErrorCode.INTERNAL_ERROR, "raw sql should not leak")
        );
        String request = "{\"attemptKey\":\"" + attemptKey + "\"}";

        String workflowId = mockMvc.perform(post(
                        "/api/projects/{projectId}/review-workflows", project.id()
                ).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.currentStage").value("SOURCE_IMPORT"))
                .andExpect(jsonPath("$.data.errorMessage")
                        .value("源码解析失败，请检查仓库和数据库迁移状态"))
                .andExpect(jsonPath("$.data.errorMessage").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("raw sql")
                )))
                .andExpect(jsonPath("$.data.recoveryAction").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        ReviewWorkflowRun saved = workflowRunMapper.selectOne(
                Wrappers.lambdaQuery(ReviewWorkflowRun.class)
                        .eq(ReviewWorkflowRun::getProjectId, project.id())
                        .last("LIMIT 1")
        );
        assertThat(saved.getRunningKey()).isNull();
        assertThat(saved.getStatus()).isEqualTo("FAILED");
        assertThat(workflowId).contains(saved.getId().toString());

        mockMvc.perform(post("/api/projects/{projectId}/review-workflows", project.id())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(saved.getId().toString()))
                .andExpect(jsonPath("$.data.status").value("FAILED"));
        verify(sourceImportService, times(1)).importSource(project.id());

        mockMvc.perform(get("/api/projects/{projectId}/review-workflows/latest", project.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(saved.getId().toString()))
                .andExpect(jsonPath("$.data.recoveryAction").isNotEmpty());
    }

    @Test
    void rejectsMalformedAttemptKeyBeforeCreatingRun() throws Exception {
        ProjectResponse project = createProject("workflow-invalid-attempt");

        mockMvc.perform(post("/api/projects/{projectId}/review-workflows", project.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attemptKey\":\"invalid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请求标识必须是小写UUID v4"));

        assertThat(workflowRunMapper.selectCount(Wrappers.lambdaQuery(ReviewWorkflowRun.class)
                .eq(ReviewWorkflowRun::getProjectId, project.id()))).isZero();
    }

    private ProjectResponse createProject(String prefix) {
        String name = prefix + "-" + UUID.randomUUID();
        return projectService.createProject(new CreateProjectRequest(
                name,
                "代码审查工作流测试",
                "GIT",
                "https://github.com/example/" + name + ".git",
                "main"
        ));
    }
}
