package com.devmate.review.service;

import com.devmate.common.error.BusinessException;
import com.devmate.knowledge.source.SourceImportException;
import com.devmate.knowledge.source.WorkspaceManager;
import com.devmate.review.config.StaticAnalysisProperties;
import com.devmate.review.source.JavaStaticAnalyzer;
import com.devmate.review.source.ProjectRuleAnalyzer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StaticAnalysisServiceTest {

    @Mock
    private StaticAnalysisStateService stateService;
    @Mock
    private WorkspaceManager workspaceManager;
    @Mock
    private JavaStaticAnalyzer analyzer;
    @Mock
    private ProjectRuleAnalyzer projectRuleAnalyzer;

    @Test
    void marksTaskFailedWhenStaticToolFails() {
        StaticAnalysisProperties properties = new StaticAnalysisProperties();
        StaticAnalysisService service = new StaticAnalysisService(
                stateService,
                workspaceManager,
                analyzer,
                projectRuleAnalyzer,
                properties
        );
        StaticAnalysisContext context = new StaticAnalysisContext(
                1L, 2L, 3L, 4L, "target-revision", List.of()
        );
        Path repositoryRoot = Path.of("/tmp/devmate-static-failure");
        given(analyzer.toolName()).willReturn("PMD");
        given(analyzer.toolVersion()).willReturn("7.26.0");
        given(stateService.prepare(1L, "PMD+DEVMATE", "7.26.0+1.0")).willReturn(context);
        given(workspaceManager.requireTaskDirectory(1L, 4L)).willReturn(repositoryRoot);
        given(analyzer.analyze(repositoryRoot.toAbsolutePath().normalize(), List.of()))
                .willThrow(new SourceImportException("PMD规则执行失败"));

        assertThatThrownBy(() -> service.create(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("PMD规则执行失败");
        verify(stateService).fail(context, "PMD规则执行失败");
    }
}
