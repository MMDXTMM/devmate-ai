package com.devmate.knowledge.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devmate.common.error.BusinessException;
import com.devmate.common.error.ErrorCode;
import com.devmate.knowledge.dto.IndexTaskResponse;
import com.devmate.knowledge.entity.IndexTask;
import com.devmate.knowledge.entity.KnowledgeDocument;
import com.devmate.knowledge.entity.KnowledgeChunk;
import com.devmate.knowledge.entity.CodeReference;
import com.devmate.knowledge.mapper.CodeReferenceMapper;
import com.devmate.knowledge.mapper.IndexTaskMapper;
import com.devmate.knowledge.mapper.KnowledgeChunkMapper;
import com.devmate.knowledge.mapper.KnowledgeDocumentMapper;
import com.devmate.knowledge.model.IndexTaskStatus;
import com.devmate.knowledge.source.GitRepositoryValidator;
import com.devmate.knowledge.source.ParsedSourceChunk;
import com.devmate.knowledge.source.ParsedCodeReference;
import com.devmate.knowledge.source.ParsedSourceFile;
import com.devmate.knowledge.source.ScannedSourceFile;
import com.devmate.project.entity.Project;
import com.devmate.project.mapper.ProjectMapper;
import com.devmate.project.model.ProjectSourceType;
import com.devmate.project.model.ProjectStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SourceImportStateService {

    private static final String TASK_TYPE_FULL = "FULL";
    private static final String TASK_TYPE_INCREMENTAL = "INCREMENTAL";
    private static final String TASK_TYPE_REBUILD = "REBUILD";
    private static final String DOCUMENT_STATUS_PARSED = "PARSED";

    private final ProjectMapper projectMapper;
    private final IndexTaskMapper indexTaskMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final CodeReferenceMapper referenceMapper;
    private final GitRepositoryValidator repositoryValidator;
    private final ObjectMapper objectMapper;
    private final List<SourceStructureUsageChecker> usageCheckers;

    public SourceImportStateService(
            ProjectMapper projectMapper,
            IndexTaskMapper indexTaskMapper,
            KnowledgeDocumentMapper documentMapper,
            KnowledgeChunkMapper chunkMapper,
            CodeReferenceMapper referenceMapper,
            GitRepositoryValidator repositoryValidator,
            ObjectMapper objectMapper,
            List<SourceStructureUsageChecker> usageCheckers
    ) {
        this.projectMapper = projectMapper;
        this.indexTaskMapper = indexTaskMapper;
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.referenceMapper = referenceMapper;
        this.repositoryValidator = repositoryValidator;
        this.objectMapper = objectMapper;
        this.usageCheckers = List.copyOf(usageCheckers);
    }

    @Transactional
    public SourceImportContext prepare(Long projectId, SourceImportMode mode) {
        Project project = projectMapper.selectByIdForUpdate(projectId);
        if (project == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "项目不存在");
        }
        if (!ProjectSourceType.GIT.name().equals(project.getSourceType())) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "第一版源码导入仅支持Git项目");
        }
        if (!StringUtils.hasText(project.getSourceLocation())) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Git项目必须填写仓库地址");
        }
        if (mode == SourceImportMode.REBUILD && !StringUtils.hasText(project.getCurrentRevision())) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "项目尚无可重建的源码结构");
        }
        repositoryValidator.validate(project.getSourceLocation());
        usageCheckers.forEach(checker -> checker.assertImportAllowed(projectId));

        LocalDateTime now = LocalDateTime.now();
        int locked = projectMapper.update(null, Wrappers.lambdaUpdate(Project.class)
                .eq(Project::getId, projectId)
                .ne(Project::getStatus, ProjectStatus.INDEXING.name())
                .set(Project::getStatus, ProjectStatus.INDEXING.name())
                .set(Project::getUpdatedAt, now));
        if (locked != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "项目源码正在导入，请勿重复提交");
        }

        IndexTask task = new IndexTask();
        task.setProjectId(projectId);
        task.setTaskType(mode == SourceImportMode.REBUILD
                ? TASK_TYPE_REBUILD
                : StringUtils.hasText(project.getCurrentRevision())
                        ? TASK_TYPE_INCREMENTAL
                        : TASK_TYPE_FULL);
        task.setStructureVersion(SourceStructureVersion.CURRENT);
        task.setStatus(IndexTaskStatus.RUNNING.name());
        task.setTotalFiles(0);
        task.setProcessedFiles(0);
        task.setReusedFiles(0);
        task.setFailedFiles(0);
        task.setCloneDurationMs(0L);
        task.setScanDurationMs(0L);
        task.setPlanDurationMs(0L);
        task.setParseDurationMs(0L);
        task.setPersistDurationMs(0L);
        task.setTotalDurationMs(0L);
        task.setCreatedAt(now);
        task.setStartedAt(now);
        indexTaskMapper.insert(task);

        String branch = StringUtils.hasText(project.getDefaultBranch())
                ? project.getDefaultBranch().trim()
                : "main";
        return new SourceImportContext(
                projectId,
                task.getId(),
                project.getSourceLocation().trim(),
                branch,
                project.getCurrentRevision(),
                project.getCurrentStructureVersion(),
                project.getStatus(),
                mode
        );
    }

    @Transactional(readOnly = true)
    public SourceImportPlan planIncremental(
            SourceImportContext context,
            List<ScannedSourceFile> scannedFiles
    ) {
        if (context.mode() == SourceImportMode.REBUILD
                || !StringUtils.hasText(context.previousRevision())
                || !SourceStructureVersion.CURRENT.equals(context.previousStructureVersion())) {
            return new SourceImportPlan(scannedFiles, List.of());
        }
        Map<String, KnowledgeDocument> previousByPathHash = loadPreviousDocuments(
                context.projectId(),
                context.previousRevision(),
                scannedFiles.stream().map(file -> file.pathHash()).collect(Collectors.toSet())
        ).stream().collect(Collectors.toMap(
                KnowledgeDocument::getPathHash,
                Function.identity(),
                (left, right) -> left
        ));
        Map<Long, List<KnowledgeChunk>> chunksByDocument = loadChunks(
                previousByPathHash.values().stream().map(KnowledgeDocument::getId).toList()
        ).stream().collect(Collectors.groupingBy(KnowledgeChunk::getDocumentId));
        List<KnowledgeChunk> reusableChunks = chunksByDocument.values().stream().flatMap(List::stream).toList();
        Map<Long, List<CodeReference>> referencesBySourceChunk = loadReferences(
                reusableChunks.stream().map(KnowledgeChunk::getId).toList()
        ).stream().collect(Collectors.groupingBy(CodeReference::getSourceChunkId));

        List<ScannedSourceFile> filesToParse = new ArrayList<>();
        List<ParsedSourceFile> reusedFiles = new ArrayList<>();
        for (ScannedSourceFile scanned : scannedFiles) {
            KnowledgeDocument previous = previousByPathHash.get(scanned.pathHash());
            if (!canReuse(previous, scanned)) {
                filesToParse.add(scanned);
                continue;
            }
            reusedFiles.add(reconstructParsedFile(
                    scanned,
                    previous,
                    chunksByDocument.getOrDefault(previous.getId(), List.of()),
                    referencesBySourceChunk
            ));
        }
        return new SourceImportPlan(filesToParse, reusedFiles);
    }

    @Transactional
    public IndexTaskResponse complete(
            SourceImportContext context,
            String revision,
            List<ParsedSourceFile> files,
            int processedFiles,
            int reusedFiles,
            SourceImportMetrics metrics
    ) {
        long persistStartedNanos = System.nanoTime();
        if (files.size() != processedFiles + reusedFiles) {
            throw new IllegalArgumentException("源码导入文件计数不一致");
        }
        if (context.mode() == SourceImportMode.REBUILD) {
            assertRebuildAllowed(context, revision);
        }
        LocalDateTime now = LocalDateTime.now();
        for (ParsedSourceFile file : files) {
            upsertDocument(context.projectId(), revision, file, now);
        }
        replaceReferences(context.projectId(), revision, files, now);

        IndexTask task = requireTask(context.taskId());
        task.setRevision(revision);
        task.setStatus(IndexTaskStatus.SUCCEEDED.name());
        task.setTotalFiles(files.size());
        task.setProcessedFiles(processedFiles);
        task.setReusedFiles(reusedFiles);
        task.setFailedFiles(0);
        applyMetrics(task, metrics, SourceImportMetrics.elapsedMillis(persistStartedNanos));
        task.setFinishedAt(now);
        indexTaskMapper.updateById(task);

        projectMapper.update(null, Wrappers.lambdaUpdate(Project.class)
                .eq(Project::getId, context.projectId())
                .set(Project::getStatus, ProjectStatus.READY.name())
                .set(Project::getCurrentRevision, revision)
                .set(Project::getCurrentStructureVersion, SourceStructureVersion.CURRENT)
                .set(Project::getLastIndexedAt, now)
                .set(Project::getUpdatedAt, now));
        return IndexTaskResponse.from(task);
    }

    @Transactional
    public IndexTaskResponse completeUnchanged(
            SourceImportContext context,
            String revision,
            SourceImportMetrics metrics
    ) {
        long persistStartedNanos = System.nanoTime();
        long documentCount = documentMapper.selectCount(Wrappers.lambdaQuery(KnowledgeDocument.class)
                .eq(KnowledgeDocument::getProjectId, context.projectId())
                .eq(KnowledgeDocument::getRevision, revision)
                .eq(KnowledgeDocument::getStructureVersion, SourceStructureVersion.CURRENT)
                .eq(KnowledgeDocument::getDeleted, 0));
        if (documentCount < 1 || documentCount > Integer.MAX_VALUE) {
            throw new IllegalStateException("当前版本缺少可复用的源码结构");
        }
        LocalDateTime now = LocalDateTime.now();
        IndexTask task = requireTask(context.taskId());
        task.setRevision(revision);
        task.setStatus(IndexTaskStatus.SUCCEEDED.name());
        task.setTotalFiles((int) documentCount);
        task.setProcessedFiles(0);
        task.setReusedFiles((int) documentCount);
        task.setFailedFiles(0);
        applyMetrics(task, metrics, SourceImportMetrics.elapsedMillis(persistStartedNanos));
        task.setFinishedAt(now);
        indexTaskMapper.updateById(task);
        projectMapper.update(null, Wrappers.lambdaUpdate(Project.class)
                .eq(Project::getId, context.projectId())
                .set(Project::getStatus, ProjectStatus.READY.name())
                .set(Project::getCurrentRevision, revision)
                .set(Project::getCurrentStructureVersion, SourceStructureVersion.CURRENT)
                .set(Project::getLastIndexedAt, now)
                .set(Project::getUpdatedAt, now));
        return IndexTaskResponse.from(task);
    }

    @Transactional
    public void fail(SourceImportContext context, String errorMessage, SourceImportMetrics metrics) {
        long persistStartedNanos = System.nanoTime();
        LocalDateTime now = LocalDateTime.now();
        IndexTask task = requireTask(context.taskId());
        task.setStatus(IndexTaskStatus.FAILED.name());
        task.setFailedFiles(Math.max(task.getFailedFiles(), 1));
        task.setErrorMessage(truncate(errorMessage));
        applyMetrics(task, metrics, SourceImportMetrics.elapsedMillis(persistStartedNanos));
        task.setFinishedAt(now);
        indexTaskMapper.updateById(task);

        projectMapper.update(null, Wrappers.lambdaUpdate(Project.class)
                .eq(Project::getId, context.projectId())
                .set(Project::getStatus, ProjectStatus.FAILED.name())
                .set(Project::getUpdatedAt, now));
    }

    @Transactional
    public void reject(SourceImportContext context, String errorMessage, SourceImportMetrics metrics) {
        long persistStartedNanos = System.nanoTime();
        LocalDateTime now = LocalDateTime.now();
        IndexTask task = requireTask(context.taskId());
        task.setStatus(IndexTaskStatus.FAILED.name());
        task.setErrorMessage(truncate(errorMessage));
        applyMetrics(task, metrics, SourceImportMetrics.elapsedMillis(persistStartedNanos));
        task.setFinishedAt(now);
        indexTaskMapper.updateById(task);

        projectMapper.update(null, Wrappers.lambdaUpdate(Project.class)
                .eq(Project::getId, context.projectId())
                .eq(Project::getStatus, ProjectStatus.INDEXING.name())
                .set(Project::getStatus, context.previousProjectStatus())
                .set(Project::getUpdatedAt, now));
    }

    @Transactional(readOnly = true)
    public void assertRebuildAllowed(SourceImportContext context, String revision) {
        if (context.mode() != SourceImportMode.REBUILD) {
            return;
        }
        if (!java.util.Objects.equals(context.previousRevision(), revision)) {
            throw new BusinessException(ErrorCode.CONFLICT, "重建只适用于当前revision，请使用普通导入处理新提交");
        }
        usageCheckers.forEach(checker -> checker.assertRebuildAllowed(context.projectId(), revision));
    }

    private void applyMetrics(IndexTask task, SourceImportMetrics metrics, long persistDurationMs) {
        task.setCloneDurationMs(metrics.cloneDurationMs());
        task.setScanDurationMs(metrics.scanDurationMs());
        task.setPlanDurationMs(metrics.planDurationMs());
        task.setParseDurationMs(metrics.parseDurationMs());
        task.setPersistDurationMs(persistDurationMs);
        task.setTotalDurationMs(metrics.totalDurationMs());
    }

    @Transactional(readOnly = true)
    public IndexTaskResponse getLatest(Long projectId) {
        if (projectMapper.selectById(projectId) == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "项目不存在");
        }
        IndexTask task = indexTaskMapper.selectOne(Wrappers.lambdaQuery(IndexTask.class)
                .eq(IndexTask::getProjectId, projectId)
                .orderByDesc(IndexTask::getCreatedAt)
                .last("LIMIT 1"));
        if (task == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "项目暂无源码导入任务");
        }
        return IndexTaskResponse.from(task);
    }

    private List<KnowledgeDocument> loadPreviousDocuments(
            Long projectId,
            String revision,
            Set<String> pathHashes
    ) {
        if (pathHashes.isEmpty()) {
            return List.of();
        }
        List<String> hashes = new ArrayList<>(pathHashes);
        List<KnowledgeDocument> result = new ArrayList<>();
        for (int offset = 0; offset < hashes.size(); offset += 500) {
            List<String> batch = hashes.subList(offset, Math.min(offset + 500, hashes.size()));
            result.addAll(documentMapper.selectList(Wrappers.lambdaQuery(KnowledgeDocument.class)
                    .eq(KnowledgeDocument::getProjectId, projectId)
                    .eq(KnowledgeDocument::getRevision, revision)
                    .eq(KnowledgeDocument::getStructureVersion, SourceStructureVersion.CURRENT)
                    .eq(KnowledgeDocument::getDeleted, 0)
                    .in(KnowledgeDocument::getStatus, "PARSED", "INDEXED")
                    .in(KnowledgeDocument::getPathHash, batch)));
        }
        return result;
    }

    private List<KnowledgeChunk> loadChunks(List<Long> documentIds) {
        if (documentIds.isEmpty()) {
            return List.of();
        }
        List<KnowledgeChunk> result = new ArrayList<>();
        for (int offset = 0; offset < documentIds.size(); offset += 500) {
            List<Long> batch = documentIds.subList(offset, Math.min(offset + 500, documentIds.size()));
            result.addAll(chunkMapper.selectList(Wrappers.lambdaQuery(KnowledgeChunk.class)
                    .in(KnowledgeChunk::getDocumentId, batch)
                    .orderByAsc(KnowledgeChunk::getDocumentId)
                    .orderByAsc(KnowledgeChunk::getChunkIndex)));
        }
        return result;
    }

    private List<CodeReference> loadReferences(List<Long> sourceChunkIds) {
        if (sourceChunkIds.isEmpty()) {
            return List.of();
        }
        List<CodeReference> result = new ArrayList<>();
        for (int offset = 0; offset < sourceChunkIds.size(); offset += 500) {
            List<Long> batch = sourceChunkIds.subList(
                    offset,
                    Math.min(offset + 500, sourceChunkIds.size())
            );
            result.addAll(referenceMapper.selectList(Wrappers.lambdaQuery(CodeReference.class)
                    .in(CodeReference::getSourceChunkId, batch)
                    .orderByAsc(CodeReference::getId)));
        }
        return result;
    }

    private boolean canReuse(
            KnowledgeDocument previous,
            ScannedSourceFile scanned
    ) {
        return previous != null
                && SourceStructureVersion.CURRENT.equals(previous.getStructureVersion())
                && previous.getFilePath().equals(scanned.relativePath())
                && previous.getFileType().equals(scanned.fileType().name())
                && previous.getContentHash().equals(scanned.contentHash());
    }

    private ParsedSourceFile reconstructParsedFile(
            ScannedSourceFile scanned,
            KnowledgeDocument document,
            List<KnowledgeChunk> storedChunks,
            Map<Long, List<CodeReference>> referencesBySourceChunk
    ) {
        List<KnowledgeChunk> orderedChunks = storedChunks.stream()
                .sorted(Comparator.comparing(KnowledgeChunk::getChunkIndex))
                .toList();
        List<ParsedSourceChunk> parsedChunks = orderedChunks.stream()
                .map(this::reconstructChunk)
                .toList();
        List<ParsedCodeReference> parsedReferences = new ArrayList<>();
        for (KnowledgeChunk sourceChunk : orderedChunks) {
            for (CodeReference reference : referencesBySourceChunk.getOrDefault(
                    sourceChunk.getId(), List.of()
            )) {
                parsedReferences.add(new ParsedCodeReference(
                        sourceChunk.getSymbolName(),
                        reference.getReferenceKind(),
                        reference.getReferenceName(),
                        reference.getQualifier(),
                        reference.getArgumentCount(),
                        reference.getStartLine(),
                        reference.getEndLine(),
                        reference.getMetadataJson()
                ));
            }
        }
        return new ParsedSourceFile(
                scanned,
                document.getPackageName(),
                parsedChunks,
                parsedReferences
        );
    }

    private ParsedSourceChunk reconstructChunk(KnowledgeChunk chunk) {
        Map<String, Object> metadata = readMetadata(chunk.getMetadataJson());
        List<String> annotations = metadata.get("annotations") instanceof List<?> values
                ? values.stream().filter(String.class::isInstance).map(String.class::cast).toList()
                : List.of();
        Integer parameterCount = metadata.get("parameterCount") instanceof Number value
                ? value.intValue()
                : null;
        return new ParsedSourceChunk(
                chunk.getChunkIndex(),
                chunk.getChunkType(),
                chunk.getSymbolName(),
                annotations,
                parameterCount,
                metadata,
                chunk.getContent(),
                chunk.getContentHash(),
                chunk.getStartLine(),
                chunk.getEndLine()
        );
    }

    private Map<String, Object> readMetadata(String metadataJson) {
        if (!StringUtils.hasText(metadataJson)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(metadataJson, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("读取源码符号元数据失败", exception);
        }
    }

    private void upsertDocument(
            Long projectId,
            String revision,
            ParsedSourceFile parsedFile,
            LocalDateTime now
    ) {
        var file = parsedFile.sourceFile();
        KnowledgeDocument document = documentMapper.selectOne(
                Wrappers.lambdaQuery(KnowledgeDocument.class)
                        .eq(KnowledgeDocument::getProjectId, projectId)
                        .eq(KnowledgeDocument::getPathHash, file.pathHash())
                        .eq(KnowledgeDocument::getRevision, revision)
                        .last("LIMIT 1")
        );
        boolean exists = document != null;
        if (!exists) {
            document = new KnowledgeDocument();
            document.setProjectId(projectId);
            document.setCreatedAt(now);
        }
        document.setSourceKind(switch (file.fileType()) {
            case JAVA -> "SOURCE_CODE";
            case YAML, PROPERTIES -> "CONFIGURATION";
            case SQL -> "DATABASE_SCHEMA";
        });
        document.setFileName(file.fileName());
        document.setFilePath(file.relativePath());
        document.setPathHash(file.pathHash());
        document.setFileType(file.fileType().name());
        document.setContentHash(file.contentHash());
        document.setRevision(revision);
        document.setStructureVersion(SourceStructureVersion.CURRENT);
        document.setPackageName(parsedFile.packageName());
        document.setStatus(DOCUMENT_STATUS_PARSED);
        document.setChunkCount(parsedFile.chunks().size());
        document.setErrorMessage(null);
        document.setDeleted(0);
        document.setUpdatedAt(now);
        if (exists) {
            documentMapper.updateById(document);
        } else {
            documentMapper.insert(document);
        }
        replaceChunks(document, revision, parsedFile.chunks(), now);
    }

    private void replaceChunks(
            KnowledgeDocument document,
            String revision,
            List<ParsedSourceChunk> chunks,
            LocalDateTime now
    ) {
        chunkMapper.delete(Wrappers.lambdaQuery(KnowledgeChunk.class)
                .eq(KnowledgeChunk::getDocumentId, document.getId()));
        for (ParsedSourceChunk parsedChunk : chunks) {
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setProjectId(document.getProjectId());
            chunk.setDocumentId(document.getId());
            chunk.setChunkIndex(parsedChunk.chunkIndex());
            chunk.setChunkType(parsedChunk.chunkType());
            chunk.setSymbolName(parsedChunk.symbolName());
            chunk.setLanguage(document.getFileType());
            chunk.setContent(parsedChunk.content());
            chunk.setContentHash(parsedChunk.contentHash());
            chunk.setStartLine(parsedChunk.startLine());
            chunk.setEndLine(parsedChunk.endLine());
            chunk.setRevision(revision);
            chunk.setMetadataJson(writeMetadata(parsedChunk.metadata()));
            chunk.setCreatedAt(now);
            chunkMapper.insert(chunk);
        }
    }

    private void replaceReferences(
            Long projectId,
            String revision,
            List<ParsedSourceFile> files,
            LocalDateTime now
    ) {
        referenceMapper.delete(Wrappers.lambdaQuery(CodeReference.class)
                .eq(CodeReference::getProjectId, projectId)
                .eq(CodeReference::getRevision, revision));

        List<KnowledgeChunk> storedChunks = chunkMapper.selectList(
                Wrappers.lambdaQuery(KnowledgeChunk.class)
                        .eq(KnowledgeChunk::getProjectId, projectId)
                        .eq(KnowledgeChunk::getRevision, revision)
        );
        Map<String, List<KnowledgeChunk>> chunksBySymbol = storedChunks.stream()
                .filter(chunk -> StringUtils.hasText(chunk.getSymbolName()))
                .collect(Collectors.groupingBy(KnowledgeChunk::getSymbolName));
        Map<String, ParsedSourceChunk> parsedChunksBySymbol = files.stream()
                .flatMap(file -> file.chunks().stream())
                .collect(Collectors.toMap(
                        ParsedSourceChunk::symbolName,
                        Function.identity(),
                        (left, right) -> left
                ));

        for (ParsedSourceFile file : files) {
            for (ParsedCodeReference parsedReference : file.references()) {
                KnowledgeChunk sourceChunk = uniqueChunk(
                        chunksBySymbol.get(parsedReference.sourceSymbolName())
                );
                if (sourceChunk == null) {
                    continue;
                }
                List<KnowledgeChunk> targetChunks = resolveTargets(
                        parsedReference,
                        sourceChunk,
                        chunksBySymbol,
                        parsedChunksBySymbol
                );
                if (targetChunks.isEmpty()) {
                    insertReference(projectId, revision, parsedReference, sourceChunk, null, now);
                    continue;
                }
                for (KnowledgeChunk targetChunk : targetChunks) {
                    insertReference(projectId, revision, parsedReference, sourceChunk, targetChunk, now);
                }
            }
        }
    }

    private void insertReference(
            Long projectId,
            String revision,
            ParsedCodeReference parsedReference,
            KnowledgeChunk sourceChunk,
            KnowledgeChunk targetChunk,
            LocalDateTime now
    ) {
        CodeReference reference = new CodeReference();
        reference.setProjectId(projectId);
        reference.setSourceChunkId(sourceChunk.getId());
        reference.setTargetChunkId(targetChunk == null ? null : targetChunk.getId());
        reference.setRevision(revision);
        reference.setReferenceKind(parsedReference.referenceKind());
        reference.setReferenceName(parsedReference.referenceName());
        reference.setQualifier(parsedReference.qualifier());
        reference.setArgumentCount(parsedReference.argumentCount());
        reference.setStartLine(parsedReference.startLine());
        reference.setEndLine(parsedReference.endLine());
        reference.setMetadataJson(parsedReference.metadataJson());
        reference.setCreatedAt(now);
        referenceMapper.insert(reference);
    }

    private List<KnowledgeChunk> resolveTargets(
            ParsedCodeReference reference,
            KnowledgeChunk sourceChunk,
            Map<String, List<KnowledgeChunk>> chunksBySymbol,
            Map<String, ParsedSourceChunk> parsedChunksBySymbol
    ) {
        if ("CONFIG_KEY".equals(reference.referenceKind())) {
            return configurationChunks(chunksBySymbol.get(reference.referenceName()));
        }
        if ("CONFIG_PREFIX".equals(reference.referenceKind())) {
            String prefix = reference.referenceName();
            return chunksBySymbol.entrySet().stream()
                    .filter(entry -> entry.getKey().equals(prefix)
                            || entry.getKey().startsWith(prefix + ".")
                            || entry.getKey().startsWith(prefix + "["))
                    .flatMap(entry -> configurationChunks(entry.getValue()).stream())
                    .toList();
        }
        if ("DATABASE_TABLE".equals(reference.referenceKind())) {
            return databaseTableChunks(chunksBySymbol.get(reference.referenceName()));
        }
        KnowledgeChunk target = resolveSameTypeTarget(
                reference, sourceChunk, chunksBySymbol, parsedChunksBySymbol
        );
        return target == null ? List.of() : List.of(target);
    }

    private List<KnowledgeChunk> configurationChunks(List<KnowledgeChunk> chunks) {
        if (chunks == null) {
            return List.of();
        }
        return chunks.stream()
                .filter(chunk -> "CONFIG_PROPERTY".equals(chunk.getChunkType()))
                .toList();
    }

    private List<KnowledgeChunk> databaseTableChunks(List<KnowledgeChunk> chunks) {
        if (chunks == null) {
            return List.of();
        }
        return chunks.stream()
                .filter(chunk -> "DATABASE_TABLE".equals(chunk.getChunkType()))
                .toList();
    }

    private KnowledgeChunk resolveSameTypeTarget(
            ParsedCodeReference reference,
            KnowledgeChunk sourceChunk,
            Map<String, List<KnowledgeChunk>> chunksBySymbol,
            Map<String, ParsedSourceChunk> parsedChunksBySymbol
    ) {
        if (!"METHOD_CALL".equals(reference.referenceKind())) {
            return null;
        }
        String sourceSymbol = sourceChunk.getSymbolName();
        int methodSeparator = sourceSymbol.indexOf('#');
        if (methodSeparator < 0 || !isSelfQualifier(reference.qualifier(), sourceSymbol.substring(0, methodSeparator))) {
            return null;
        }
        String ownerType = sourceSymbol.substring(0, methodSeparator);
        String targetPrefix = ownerType + "#" + reference.referenceName() + "(";
        List<KnowledgeChunk> candidates = chunksBySymbol.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(targetPrefix))
                .filter(entry -> {
                    ParsedSourceChunk parsed = parsedChunksBySymbol.get(entry.getKey());
                    return parsed != null && parsed.parameterCount() != null
                            && parsed.parameterCount().equals(reference.argumentCount());
                })
                .flatMap(entry -> entry.getValue().stream())
                .toList();
        return uniqueChunk(candidates);
    }

    private boolean isSelfQualifier(String qualifier, String ownerType) {
        if (!StringUtils.hasText(qualifier) || "this".equals(qualifier)) {
            return true;
        }
        String simpleOwner = ownerType.substring(ownerType.lastIndexOf('.') + 1);
        return simpleOwner.equals(qualifier);
    }

    private KnowledgeChunk uniqueChunk(List<KnowledgeChunk> chunks) {
        return chunks != null && chunks.size() == 1 ? chunks.getFirst() : null;
    }

    private String writeMetadata(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("序列化源码符号元数据失败", exception);
        }
    }

    private IndexTask requireTask(Long taskId) {
        IndexTask task = indexTaskMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalStateException("源码导入任务不存在");
        }
        return task;
    }

    private String truncate(String value) {
        if (!StringUtils.hasText(value)) {
            return "源码导入失败";
        }
        String trimmed = value.trim();
        return trimmed.length() <= 1000 ? trimmed : trimmed.substring(0, 1000);
    }
}
