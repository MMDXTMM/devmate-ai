package com.devmate.knowledge.service;

import com.devmate.common.error.BusinessException;
import com.devmate.common.error.ErrorCode;
import com.devmate.knowledge.dto.IndexTaskResponse;
import com.devmate.knowledge.source.GitCloneResult;
import com.devmate.knowledge.source.GitSourceClient;
import com.devmate.knowledge.source.ConfigurationFileParser;
import com.devmate.knowledge.source.JavaSourceParser;
import com.devmate.knowledge.source.ParsedSourceFile;
import com.devmate.knowledge.source.ProjectSourceScanner;
import com.devmate.knowledge.source.ScannedSourceFile;
import com.devmate.knowledge.source.SourceFileType;
import com.devmate.knowledge.source.SourceImportException;
import com.devmate.knowledge.source.WorkspaceManager;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

@Service
public class SourceImportService {

    private final SourceImportStateService stateService;
    private final WorkspaceManager workspaceManager;
    private final GitSourceClient gitSourceClient;
    private final ProjectSourceScanner sourceScanner;
    private final JavaSourceParser sourceParser;
    private final ConfigurationFileParser configurationFileParser;

    public SourceImportService(
            SourceImportStateService stateService,
            WorkspaceManager workspaceManager,
            GitSourceClient gitSourceClient,
            ProjectSourceScanner sourceScanner,
            JavaSourceParser sourceParser,
            ConfigurationFileParser configurationFileParser
    ) {
        this.stateService = stateService;
        this.workspaceManager = workspaceManager;
        this.gitSourceClient = gitSourceClient;
        this.sourceScanner = sourceScanner;
        this.sourceParser = sourceParser;
        this.configurationFileParser = configurationFileParser;
    }

    public IndexTaskResponse importSource(Long projectId) {
        SourceImportContext context = stateService.prepare(projectId);
        try {
            Path taskDirectory = workspaceManager.createTaskDirectory(projectId, context.taskId());
            GitCloneResult clone = gitSourceClient.cloneRepository(
                    context.repositoryUrl(),
                    context.branch(),
                    taskDirectory
            );
            List<ScannedSourceFile> files = sourceScanner.scan(clone.repositoryRoot());
            if (files.stream().noneMatch(file -> file.fileType() == SourceFileType.JAVA)) {
                throw new SourceImportException("仓库中没有找到Java源码文件");
            }
            List<ParsedSourceFile> parsedFiles = files.stream()
                    .map(file -> file.fileType() == SourceFileType.JAVA
                            ? sourceParser.parse(file)
                            : configurationFileParser.parse(file))
                    .toList();
            return stateService.complete(context, clone.revision(), parsedFiles);
        } catch (RuntimeException exception) {
            String message = exception.getMessage() == null ? "源码导入失败" : exception.getMessage();
            stateService.fail(context, message);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, message);
        }
    }

    public IndexTaskResponse getLatestTask(Long projectId) {
        return stateService.getLatest(projectId);
    }
}
