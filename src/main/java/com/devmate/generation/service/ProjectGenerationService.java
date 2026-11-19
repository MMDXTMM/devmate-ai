package com.devmate.generation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devmate.agent.model.ModelConnectionSnapshot;
import com.devmate.agent.service.ModelConnectionService;
import com.devmate.common.error.BusinessException;
import com.devmate.common.error.ErrorCode;
import com.devmate.generation.dto.*;
import com.devmate.generation.entity.GenerationRun;
import com.devmate.generation.mapper.GenerationRunMapper;
import com.devmate.generation.model.GenerationModel;
import com.devmate.generation.model.ProjectBundle;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProjectGenerationService {
    private final GenerationTaskService tasks;
    private final GenerationSessionService sessions;
    private final GenerationRunMapper runs;
    private final ModelConnectionService connections;
    private final GenerationModel model;
    private final GeneratedProjectStore store;
    private final ObjectMapper json;
    private final TaskExecutor executor;
    private final TransactionTemplate transactions;

    public ProjectGenerationService(GenerationTaskService tasks, GenerationSessionService sessions, GenerationRunMapper runs,
                                    ModelConnectionService connections, GenerationModel model,
                                    GeneratedProjectStore store, ObjectMapper json,
                                    @Qualifier("generationTaskExecutor") TaskExecutor executor,
                                    PlatformTransactionManager transactionManager) {
        this.tasks=tasks; this.sessions = sessions; this.runs = runs; this.connections = connections; this.model = model;
        this.store = store; this.json = json; this.executor = executor;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public GenerationRunResponse start(Long sessionId, GenerateProjectRequest request) {
        var session = sessions.getSession(sessionId);
        if (!"CONFIRMED".equals(session.status()) || !request.versionId().toString().equals(session.confirmedVersionId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "请先确认当前需求方案，再生成项目");
        }
        GenerationRun existing = latestRun(sessionId);
        if (existing != null && (!java.util.Set.of("FAILED","CANCELLED").contains(existing.getStatus()) || existing.getAttemptKey().equals(request.attemptKey()))) return response(existing);
        ModelConnectionSnapshot connection = connections.requireActiveConnection();
        GenerationRun prepared;
        try {
            prepared = transactions.execute(ignored -> {
                LocalDateTime now = LocalDateTime.now();
                if (existing == null) {
                    GenerationRun run = new GenerationRun();
                    run.setSessionId(sessionId); run.setVersionId(request.versionId()); run.setAttemptKey(request.attemptKey());
                    run.setStatus("RUNNING"); run.setManifestJson("[]"); run.setWarningsJson("[]");
                    run.setCreatedAt(now); run.setUpdatedAt(now); runs.insert(run); return run;
                }
                int updated = runs.update(null, Wrappers.lambdaUpdate(GenerationRun.class)
                        .eq(GenerationRun::getId, existing.getId()).in(GenerationRun::getStatus, "FAILED","CANCELLED")
                        .eq(GenerationRun::getAttemptKey, existing.getAttemptKey())
                        .set(GenerationRun::getAttemptKey, request.attemptKey()).set(GenerationRun::getStatus, "RUNNING")
                        .set(GenerationRun::getErrorMessage, null).set(GenerationRun::getUpdatedAt, now));
                if (updated != 1) throw new BusinessException(ErrorCode.CONFLICT, "生成任务已更新，请刷新查看");
                return runs.selectById(existing.getId());
            });
        } catch (DuplicateKeyException duplicate) { return response(latestRun(sessionId)); }
        try { tasks.submit("GENERATE",prepared.getId(),sessionId,prepared.getAttemptKey(),executor,() -> execute(prepared, connection, session.latestSpec())); }
        catch (RuntimeException rejected) { fail(prepared, "生成队列已满或暂不可用，请稍后重试"); }
        return response(runs.selectById(prepared.getId()));
    }

    public GenerationRunResponse latest(Long sessionId) {
        sessions.getSession(sessionId);
        GenerationRun run = latestRun(sessionId);
        if (run == null) return null;
        if ("RUNNING".equals(run.getStatus()) && run.getUpdatedAt().isBefore(LocalDateTime.now().minusMinutes(10))) {
            fail(run, "生成已超时或服务重启中断，可保留方案重新生成");
            run = runs.selectById(run.getId());
        }
        return response(run);
    }

    public GeneratedFileResponse file(Long sessionId, Long runId, String path) {
        GenerationRun run = ownedReady(sessionId, runId);
        if (manifest(run).stream().noneMatch(file -> file.path().equals(path))) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "生成文件不存在");
        try { return new GeneratedFileResponse(path, store.read(run, path)); }
        catch (IOException exception) { throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "生成文件已不可用，请检查工作区存储"); }
    }
    public byte[] download(Long sessionId, Long runId) {
        try { return store.download(ownedReady(sessionId, runId)); }
        catch (IOException exception) { throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "源码包已不可用，请检查工作区存储"); }
    }

    private GenerationRun ownedReady(Long sessionId, Long runId) {
        sessions.getSession(sessionId);
        GenerationRun run = runs.selectById(runId);
        if (run == null || !sessionId.equals(run.getSessionId())) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "生成任务不存在");
        if (!"SUCCEEDED".equals(run.getStatus())) throw new BusinessException(ErrorCode.CONFLICT, "项目尚未生成完成");
        return run;
    }
    private GenerationRun latestRun(Long sessionId) {
        return runs.selectOne(Wrappers.lambdaQuery(GenerationRun.class).eq(GenerationRun::getSessionId, sessionId)
                .orderByDesc(GenerationRun::getCreatedAt).orderByDesc(GenerationRun::getId).last("LIMIT 1"));
    }
    private void execute(GenerationRun run, ModelConnectionSnapshot connection, GenerationSpecResponse spec) {
        try {
            GenerationTaskService.phase("AI 正在生成源码");
            ProjectBundle bundle = model.generate(connection, spec);
            GenerationTaskService.phase("检查文件并打包");
            var files = store.write(run, bundle);
            GenerationTaskService.publish(()->runs.update(null, currentRun(run).set(GenerationRun::getStatus, "SUCCEEDED")
                    .set(GenerationRun::getSummary, bundle.summary()).set(GenerationRun::getRunInstructions, bundle.runInstructions())
                    .set(GenerationRun::getManifestJson, encode(files)).set(GenerationRun::getWarningsJson, encode(bundle.warnings()))
                    .set(GenerationRun::getUpdatedAt, LocalDateTime.now())));
        } catch (Exception exception) {
            fail(run, exception instanceof BusinessException ? exception.getMessage() : "源码生成或保存失败，已保留确认方案，请稍后重试或缩小项目范围");
        }
    }
    private com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<GenerationRun> currentRun(GenerationRun run) {
        return Wrappers.lambdaUpdate(GenerationRun.class).eq(GenerationRun::getId, run.getId())
                .eq(GenerationRun::getStatus, "RUNNING").eq(GenerationRun::getAttemptKey, run.getAttemptKey());
    }
    private void fail(GenerationRun run, String message) {
        runs.update(null, currentRun(run).set(GenerationRun::getStatus, "FAILED")
                .set(GenerationRun::getErrorMessage, message.substring(0, Math.min(message.length(), 500)))
                .set(GenerationRun::getUpdatedAt, LocalDateTime.now()));
    }
    private GenerationRunResponse response(GenerationRun run) {
        return new GenerationRunResponse(run.getId().toString(), run.getSessionId().toString(), run.getVersionId().toString(),
                run.getStatus(), run.getSummary(), run.getRunInstructions(), manifest(run),
                decode(run.getWarningsJson(), new TypeReference<>() { }), "STRUCTURE_ONLY", run.getErrorMessage(),
                run.getCreatedAt(), run.getUpdatedAt());
    }
    private List<GenerationRunResponse.FileEntry> manifest(GenerationRun run) { return decode(run.getManifestJson(), new TypeReference<>() { }); }
    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("生成记录无法保存", exception); }
    }
    private <T> T decode(String value, TypeReference<T> type) {
        try { return json.readValue(value, type); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("生成记录无法读取", exception); }
    }
}
