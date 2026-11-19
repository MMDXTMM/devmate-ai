package com.devmate.generation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devmate.agent.model.ModelConnectionSnapshot;
import com.devmate.agent.service.ModelConnectionService;
import com.devmate.common.error.*;
import com.devmate.generation.dto.*;
import com.devmate.generation.entity.*;
import com.devmate.generation.mapper.GenerationVerificationMapper;
import com.devmate.generation.model.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class GenerationVerificationService {
    private final GenerationTaskService tasks;
    private final GenerationRevisionService revisions;
    private final GenerationVerificationMapper checks;
    private final GenerationSandbox sandbox;
    private final GenerationModel model;
    private final ModelConnectionService connections;
    private final TaskExecutor executor;
    public GenerationVerificationService(GenerationTaskService tasks, GenerationRevisionService revisions,GenerationVerificationMapper checks,
            GenerationSandbox sandbox,GenerationModel model,ModelConnectionService connections,
            @Qualifier("generationTaskExecutor") TaskExecutor executor) {
        this.tasks=tasks;this.revisions=revisions;this.checks=checks;this.sandbox=sandbox;this.model=model;this.connections=connections;this.executor=executor;
    }
    public VerificationResponse start(Long sid,Long revisionId,VerificationRequest request) {
        var revision=revisions.ready(sid,revisionId);
        var existing=checks.selectOne(Wrappers.lambdaQuery(GenerationVerification.class).eq(GenerationVerification::getRevisionId,revisionId).eq(GenerationVerification::getAttemptKey,request.attemptKey()));
        if(existing!=null) return response(existing);
        var active=checks.selectOne(Wrappers.lambdaQuery(GenerationVerification.class).eq(GenerationVerification::getSessionId,sid).eq(GenerationVerification::getStatus,"RUNNING").last("LIMIT 1"));
        if(active!=null) throw new BusinessException(ErrorCode.CONFLICT,"此项目已有验证任务，请先等待完成");
        ModelConnectionSnapshot connection=request.repairLimit()>0?connections.requireActiveConnection():null;
        var check=new GenerationVerification();check.setRevisionId(revisionId);check.setSessionId(sid);check.setAttemptKey(request.attemptKey());check.setStatus("RUNNING");
        check.setRunningKey(sid);check.setRepairLimit(request.repairLimit());check.setRepairs(0);check.setCreatedAt(LocalDateTime.now());check.setUpdatedAt(check.getCreatedAt());
        try { checks.insert(check); } catch(DuplicateKeyException duplicate) {
            var duplicateCheck=checks.selectOne(Wrappers.lambdaQuery(GenerationVerification.class).eq(GenerationVerification::getRevisionId,revisionId).eq(GenerationVerification::getAttemptKey,request.attemptKey()));
            if(duplicateCheck==null) throw new BusinessException(ErrorCode.CONFLICT,"此项目已有验证任务，请等待完成");
            return response(duplicateCheck);
        }
        try { tasks.submit("VERIFY",check.getId(),sid,check.getAttemptKey(),executor,()->execute(check,revision,connection)); }
        catch(RuntimeException rejected) { finish(check,"BLOCKED",null,null,"验证队列已满，请稍后重试"); }
        return response(checks.selectById(check.getId()));
    }
    public VerificationResponse latest(Long sid,Long revisionId) {
        revisions.owned(sid,revisionId);
        var c=checks.selectOne(Wrappers.lambdaQuery(GenerationVerification.class).eq(GenerationVerification::getRevisionId,revisionId).orderByDesc(GenerationVerification::getCreatedAt).orderByDesc(GenerationVerification::getId).last("LIMIT 1"));
        if(c!=null && "RUNNING".equals(c.getStatus()) && c.getUpdatedAt().isBefore(LocalDateTime.now().minusMinutes(15))) {
            finish(c,"BLOCKED",null,null,"验证已中断或超时，请重新发起");c=checks.selectById(c.getId());
        }
        return c==null?null:response(c);
    }
    private void execute(GenerationVerification check,GenerationRevision revision,ModelConnectionSnapshot connection) {
        try {
            GenerationTaskService.phase("隔离环境正在验证");
            var original=revisions.bundle(revision); var current=original;var result=sandbox.verify(current);var candidate=revision;
            while(result.status().equals("FAILED") && check.getRepairs()<check.getRepairLimit()) {
                if(!running(check)) return;
                GenerationTaskService.phase("AI 正在根据失败证据修复 · 第"+(check.getRepairs()+1)+"次");
                var repaired=model.repair(connection,current,result);
                preserveChecks(original,repaired);
                if(!running(check)) return;
                check.setRepairs(check.getRepairs()+1);
                candidate=revisions.saveRepair(candidate,repaired,"repair-"+check.getId()+"-"+check.getRepairs());
                current=repaired; GenerationTaskService.phase("重新验证修复后的源码"); result=sandbox.verify(current);
                checks.update(null,active(check).set(GenerationVerification::getRepairs,check.getRepairs()).set(GenerationVerification::getUpdatedAt,LocalDateTime.now()));
            }
            finish(check,result.status(),result,candidate.getId(),null);
        } catch(Exception e) { finish(check,"FAILED",null,null,e instanceof BusinessException?e.getMessage():"验证或修复失败，原源码版本保持不变"); }
    }
    static void preserveChecks(ProjectBundle original,ProjectBundle repaired) {
        if(repaired==null || repaired.files()==null) throw new BusinessException(ErrorCode.INTERNAL_ERROR,"AI修复没有交付完整源码");
        Map<String,String> before=new HashMap<>(),after=new HashMap<>(); original.files().forEach(f->before.put(f.path(),f.content()));repaired.files().forEach(f->after.put(f.path(),f.content()));
        if(!before.keySet().equals(after.keySet())) throw new BusinessException(ErrorCode.INVALID_ARGUMENT,"修复不得删除或新增原文件，请使用修改项目功能调整范围");
        for(String path:before.keySet()) {
            String lower=path.toLowerCase(Locale.ROOT);
            if((lower.contains("test")||lower.contains("spec")||lower.endsWith(".json")||lower.endsWith(".toml")||lower.endsWith(".xml")||lower.endsWith(".gradle")||lower.endsWith(".txt")) && !Objects.equals(before.get(path),after.get(path)))
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT,"修复不得修改测试、数据或依赖配置以规避验证");
        }
    }
    private boolean running(GenerationVerification c) { return "RUNNING".equals(checks.selectById(c.getId()).getStatus()); }
    private com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<GenerationVerification> active(GenerationVerification c) { return Wrappers.lambdaUpdate(GenerationVerification.class).eq(GenerationVerification::getId,c.getId()).eq(GenerationVerification::getStatus,"RUNNING"); }
    private void finish(GenerationVerification c,String status,VerificationResult result,Long resultId,String error) {
        GenerationTaskService.publish(()->checks.update(null,active(c).set(GenerationVerification::getStatus,status).set(GenerationVerification::getRunningKey,null).set(GenerationVerification::getResultJson,result==null?null:revisions.encode(result))
            .set(GenerationVerification::getProfile,result==null?null:result.profile()).set(GenerationVerification::getResultRevisionId,resultId)
            .set(GenerationVerification::getErrorMessage,error).set(GenerationVerification::getUpdatedAt,LocalDateTime.now())));
    }
    private VerificationResponse response(GenerationVerification c) { return new VerificationResponse(c.getId().toString(),c.getRevisionId().toString(),c.getStatus(),c.getRepairs(),c.getRepairLimit(),c.getResultRevisionId()==null?null:c.getResultRevisionId().toString(),c.getResultJson()==null?null:revisions.decode(c.getResultJson(),new com.fasterxml.jackson.core.type.TypeReference<VerificationResult>(){}),c.getErrorMessage(),c.getCreatedAt(),c.getUpdatedAt()); }
}
