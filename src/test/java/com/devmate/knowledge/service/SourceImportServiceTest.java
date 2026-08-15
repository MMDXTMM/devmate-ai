package com.devmate.knowledge.service;

import com.devmate.common.error.BusinessException;
import com.devmate.knowledge.source.ConfigurationFileParser;
import com.devmate.knowledge.source.DatabaseSchemaParser;
import com.devmate.knowledge.source.GitCloneResult;
import com.devmate.knowledge.source.GitSourceClient;
import com.devmate.knowledge.source.JavaSourceParser;
import com.devmate.knowledge.source.ParsedSourceFile;
import com.devmate.knowledge.source.ProjectSourceScanner;
import com.devmate.knowledge.source.ScannedSourceFile;
import com.devmate.knowledge.source.SourceFileType;
import com.devmate.knowledge.source.WorkspaceManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SourceImportServiceTest {

    @Mock
    private SourceImportStateService stateService;
    @Mock
    private WorkspaceManager workspaceManager;
    @Mock
    private GitSourceClient gitSourceClient;
    @Mock
    private ProjectSourceScanner sourceScanner;
    @Mock
    private JavaSourceParser sourceParser;
    @Mock
    private ConfigurationFileParser configurationFileParser;
    @Mock
    private DatabaseSchemaParser databaseSchemaParser;

    @Test
    void hidesRawSqlWhenSourceChunkExceedsDatabaseCapacity() {
        SourceImportService service = new SourceImportService(
                stateService,
                workspaceManager,
                gitSourceClient,
                sourceScanner,
                sourceParser,
                configurationFileParser,
                databaseSchemaParser
        );
        SourceImportContext context = new SourceImportContext(
                1L, 2L, "https://github.com/example/demo.git", "main",
                null, null, "CREATED", SourceImportMode.STANDARD
        );
        Path taskDirectory = Path.of("/tmp/devmate-source-import-test");
        ScannedSourceFile scanned = new ScannedSourceFile(
                "Large.java", "src/Large.java", SourceFileType.JAVA,
                "path-hash", "content-hash", 70_000, taskDirectory.resolve("Large.java")
        );
        ParsedSourceFile parsed = new ParsedSourceFile(scanned, "example", List.of(), List.of());
        given(stateService.prepare(1L, SourceImportMode.STANDARD)).willReturn(context);
        given(workspaceManager.createTaskDirectory(1L, 2L)).willReturn(taskDirectory);
        given(gitSourceClient.cloneRepository(context.repositoryUrl(), "main", taskDirectory))
                .willReturn(new GitCloneResult(taskDirectory, "0123456789abcdef"));
        given(sourceScanner.scan(taskDirectory)).willReturn(List.of(scanned));
        given(stateService.planIncremental(context, List.of(scanned)))
                .willReturn(new SourceImportPlan(List.of(scanned), List.of()));
        given(sourceParser.parse(scanned)).willReturn(parsed);
        given(stateService.complete(
                eq(context), eq("0123456789abcdef"), any(), eq(1), eq(0), any()
        )).willThrow(new DataIntegrityViolationException(
                "INSERT INTO knowledge_chunk ... Data too long for column 'content' at row 1"
        ));

        assertThatThrownBy(() -> service.importSource(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("源码块超过数据库存储限制，请确认数据库迁移已完成后重新解析")
                .hasMessageNotContaining("INSERT INTO");
        verify(stateService).fail(
                eq(context),
                eq("源码块超过数据库存储限制，请确认数据库迁移已完成后重新解析"),
                any()
        );
    }
}
