package com.devmate.review.service;

import com.devmate.common.error.BusinessException;
import com.devmate.project.dto.CreateProjectRequest;
import com.devmate.project.dto.ProjectResponse;
import com.devmate.project.service.ProjectService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReviewWorkflowStateServiceTest {

    @Autowired private ReviewWorkflowStateService stateService;
    @Autowired private ProjectService projectService;

    @Test
    void preventsConcurrentRunsAndReusesTheSameAttempt() {
        ProjectResponse project = projectService.createProject(new CreateProjectRequest(
                "workflow-concurrency-" + UUID.randomUUID(),
                "工作流并发测试",
                "GIT",
                "https://github.com/example/workflow-concurrency.git",
                "main"
        ));
        String attemptKey = UUID.randomUUID().toString();

        ReviewWorkflowStart first = stateService.prepare(project.id(), attemptKey);
        ReviewWorkflowStart repeated = stateService.prepare(project.id(), attemptKey);

        assertThat(first.created()).isTrue();
        assertThat(repeated.created()).isFalse();
        assertThat(repeated.run().getId()).isEqualTo(first.run().getId());
        assertThatThrownBy(() -> stateService.prepare(project.id(), UUID.randomUUID().toString()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("当前项目已有代码审查正在运行");
    }
}
