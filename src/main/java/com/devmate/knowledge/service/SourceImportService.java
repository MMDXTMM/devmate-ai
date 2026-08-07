package com.devmate.knowledge.service;

import com.devmate.common.error.BusinessException;
import com.devmate.common.error.ErrorCode;
import com.devmate.knowledge.dto.IndexTaskResponse;
import com.devmate.knowledge.source.GitCloneResult;
import com.devmate.knowledge.source.GitSourceClient;
import com.devmate.knowledge.source.ConfigurationFileParser;
import com.devmate.knowledge.source.DatabaseSchemaParser;
import com.devmate.knowledge.source.JavaSourceParser;
import com.devmate.knowledge.source.ParsedSourceFile;
import com.devmate.knowledge.source.ProjectSourceScanner;
import com.devmate.knowledge.source.ScannedSourceFile;
import com.devmate.knowledge.source.SourceFileType;
import com.devmate.knowledge.source.SourceImportException;
import com.devmate.knowledge.source.WorkspaceManager;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class SourceImportService {

    private final SourceImportStateService stateService;
    private final WorkspaceManager workspaceManager;
    private final GitSourceClient gitSourceClient;
    private final ProjectSourceScanner sourceScanner;
    private final JavaSourceParser sourceParser;
    private final ConfigurationFileParser configurationFileParser;
    private final DatabaseSchemaParser databaseSchemaParser;

    public SourceImportService(
            SourceImportStateService stateService,
            WorkspaceManager workspaceManager,
            GitSourceClient gitSourceClient,
            ProjectSourceScanner sourceScanner,
            JavaSourceParser sourceParser,
            ConfigurationFileParser configurationFileParser,
            DatabaseSchemaParser databaseSchemaParser
    ) {
        this.stateService = stateService;
        this.workspaceManager = workspaceManager;
        this.gitSourceClient = gitSourceClient;
        this.sourceScanner = sourceScanner;
        this.sourceParser = sourceParser;
        this.configurationFileParser = configurationFileParser;
        this.databaseSchemaParser = databaseSchemaParser;
    }

    public IndexTaskResponse importSource(Long projectId) {
        return importSource(projectId, SourceImportMode.STANDARD);
    }

    public IndexTaskResponse rebuildSource(Long projectId) {
        return importSource(projectId, SourceImportMode.REBUILD);
    }

    private IndexTaskResponse importSource(Long projectId, SourceImportMode mode) {
        long totalStartedNanos = System.nanoTime();
        SourceImportContext context = stateService.prepare(projectId, mode);
        SourceImportMetrics metrics = SourceImportMetrics.empty(totalStartedNanos);
        try {
            Path taskDirectory = workspaceManager.createTaskDirectory(projectId, context.taskId());
            long cloneStartedNanos = System.nanoTime();
            GitCloneResult clone;
            try {
                clone = gitSourceClient.cloneRepository(
                        context.repositoryUrl(),
                        context.branch(),
                        taskDirectory
                );
            } catch (RuntimeException exception) {
                metrics = metrics.withCloneDuration(SourceImportMetrics.elapsedMillis(cloneStartedNanos));
                throw exception;
            }
            metrics = metrics.withCloneDuration(SourceImportMetrics.elapsedMillis(cloneStartedNanos));
            if (Objects.equals(context.previousRevision(), clone.revision())) {
                if (mode == SourceImportMode.REBUILD) {
                    stateService.assertRebuildAllowed(context, clone.revision());
                } else if (SourceStructureVersion.CURRENT.equals(context.previousStructureVersion())) {
                    return stateService.completeUnchanged(context, clone.revision(), metrics);
                } else {
                    throw new BusinessException(
                            ErrorCode.CONFLICT,
                            "当前revision使用旧版源码结构，请执行显式重建"
                    );
                }
            } else if (mode == SourceImportMode.REBUILD) {
                throw new BusinessException(
                        ErrorCode.CONFLICT,
                        "远端仓库已有新提交，请先执行普通源码导入"
                );
            }
            long scanStartedNanos = System.nanoTime();
            List<ScannedSourceFile> files;
            try {
                files = sourceScanner.scan(clone.repositoryRoot());
            } catch (RuntimeException exception) {
                metrics = metrics.withScanDuration(SourceImportMetrics.elapsedMillis(scanStartedNanos));
                throw exception;
            }
            metrics = metrics.withScanDuration(SourceImportMetrics.elapsedMillis(scanStartedNanos));
            if (files.stream().noneMatch(file -> file.fileType() == SourceFileType.JAVA)) {
                throw new SourceImportException("仓库中没有找到Java源码文件");
            }
            long planStartedNanos = System.nanoTime();
            SourceImportPlan plan;
            try {
                plan = stateService.planIncremental(context, files);
            } catch (RuntimeException exception) {
                metrics = metrics.withPlanDuration(SourceImportMetrics.elapsedMillis(planStartedNanos));
                throw exception;
            }
            metrics = metrics.withPlanDuration(SourceImportMetrics.elapsedMillis(planStartedNanos));
            long parseStartedNanos = System.nanoTime();
            List<ParsedSourceFile> parsedFiles = new ArrayList<>(plan.reusedFiles());
            try {
                parsedFiles.addAll(plan.filesToParse().stream()
                        .map(this::parse)
                        .toList());
            } catch (RuntimeException exception) {
                metrics = metrics.withParseDuration(SourceImportMetrics.elapsedMillis(parseStartedNanos));
                throw exception;
            }
            metrics = metrics.withParseDuration(SourceImportMetrics.elapsedMillis(parseStartedNanos));
            return stateService.complete(
                    context,
                    clone.revision(),
                    parsedFiles,
                    plan.filesToParse().size(),
                    plan.reusedFiles().size(),
                    metrics
            );
        } catch (RuntimeException exception) {
            String message = exception.getMessage() == null ? "源码导入失败" : exception.getMessage();
            if (exception instanceof BusinessException businessException
                    && businessException.getErrorCode() == ErrorCode.CONFLICT) {
                stateService.reject(context, message, metrics);
                throw businessException;
            }
            stateService.fail(context, message, metrics);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, message);
        }
    }

    public IndexTaskResponse getLatestTask(Long projectId) {
        return stateService.getLatest(projectId);
    }

    private ParsedSourceFile parse(ScannedSourceFile file) {
        return switch (file.fileType()) {
            case JAVA -> sourceParser.parse(file);
            case YAML, PROPERTIES -> configurationFileParser.parse(file);
            case SQL -> databaseSchemaParser.parse(file);
        };
    }
}
