package com.devmate.review.service;

import com.devmate.common.error.BusinessException;
import com.devmate.common.error.ErrorCode;
import com.devmate.knowledge.source.SourceImportException;
import com.devmate.knowledge.source.WorkspaceManager;
import com.devmate.review.config.StaticAnalysisProperties;
import com.devmate.review.dto.StaticAnalysisResponse;
import com.devmate.review.model.StaticAnalysisResult;
import com.devmate.review.model.StaticAnalysisTarget;
import com.devmate.review.source.JavaStaticAnalyzer;
import com.devmate.review.source.ProjectRuleAnalyzer;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class StaticAnalysisService {

    private final StaticAnalysisStateService stateService;
    private final WorkspaceManager workspaceManager;
    private final JavaStaticAnalyzer analyzer;
    private final ProjectRuleAnalyzer projectRuleAnalyzer;
    private final StaticAnalysisProperties properties;

    public StaticAnalysisService(
            StaticAnalysisStateService stateService,
            WorkspaceManager workspaceManager,
            JavaStaticAnalyzer analyzer,
            ProjectRuleAnalyzer projectRuleAnalyzer,
            StaticAnalysisProperties properties
    ) {
        this.stateService = stateService;
        this.workspaceManager = workspaceManager;
        this.analyzer = analyzer;
        this.projectRuleAnalyzer = projectRuleAnalyzer;
        this.properties = properties;
    }

    public StaticAnalysisResponse create(Long projectId) {
        StaticAnalysisContext context = stateService.prepare(
                projectId,
                analyzer.toolName() + "+DEVMATE",
                analyzer.toolVersion() + "+" + ProjectRuleAnalyzer.TOOL_VERSION
        );
        try {
            Path repositoryRoot = workspaceManager.requireTaskDirectory(
                    projectId,
                    context.indexTaskId()
            ).toAbsolutePath().normalize();
            List<StaticAnalysisTarget> targets = createTargets(repositoryRoot, context.files());
            StaticAnalysisResult toolResult = analyzeWithTimeout(repositoryRoot, targets);
            StaticAnalysisResult projectResult = projectRuleAnalyzer.analyze(context, targets);
            List<com.devmate.review.model.StaticFinding> findings = new java.util.ArrayList<>(
                    toolResult.findings()
            );
            findings.addAll(projectResult.findings());
            StaticAnalysisResult result = new StaticAnalysisResult(
                    analyzer.toolName() + "+DEVMATE",
                    analyzer.toolVersion() + "+" + ProjectRuleAnalyzer.TOOL_VERSION,
                    targets.size(),
                    findings
            );
            return stateService.complete(context, result);
        } catch (RuntimeException exception) {
            String message = exception.getMessage() == null ? "静态分析执行失败" : exception.getMessage();
            stateService.fail(context, message);
            if (exception instanceof BusinessException businessException) {
                throw businessException;
            }
            if (exception instanceof SourceImportException) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, message);
            }
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, message);
        }
    }

    public StaticAnalysisResponse getLatest(Long projectId) {
        return stateService.getLatest(projectId);
    }

    private List<StaticAnalysisTarget> createTargets(
            Path repositoryRoot,
            List<StaticAnalysisFileContext> files
    ) {
        if (files.size() > properties.getMaxFiles()) {
            throw new SourceImportException("本次变更Java文件超过静态分析数量限制");
        }
        return files.stream().map(file -> {
            Path sourcePath = repositoryRoot.resolve(file.relativePath()).normalize();
            if (!sourcePath.startsWith(repositoryRoot)
                    || Files.isSymbolicLink(sourcePath)
                    || !Files.isRegularFile(sourcePath)) {
                throw new SourceImportException("静态分析文件不在受控工作区：" + file.relativePath());
            }
            return new StaticAnalysisTarget(file.relativePath(), sourcePath, file.changedLines());
        }).toList();
    }

    private StaticAnalysisResult analyzeWithTimeout(
            Path repositoryRoot,
            List<StaticAnalysisTarget> targets
    ) {
        FutureTask<StaticAnalysisResult> task = new FutureTask<>(
                () -> analyzer.analyze(repositoryRoot, targets)
        );
        Thread worker = Thread.ofVirtual().name("devmate-static-analysis").start(task);
        try {
            return task.get(properties.getTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException exception) {
            task.cancel(true);
            worker.interrupt();
            throw new SourceImportException("静态分析执行超时");
        } catch (InterruptedException exception) {
            task.cancel(true);
            Thread.currentThread().interrupt();
            throw new SourceImportException("静态分析被中断", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new SourceImportException("静态分析执行失败", cause);
        }
    }
}
