package com.devmate.generation.service;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devmate.agent.service.ModelConnectionService;
import com.devmate.common.error.*;
import com.devmate.generation.dto.*;
import com.devmate.generation.entity.*;
import com.devmate.generation.mapper.*;
import com.devmate.generation.model.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DuplicateKeyException;
import java.time.LocalDateTime;
@Service
public class GenerationChangeService {
    private final GenerationTaskService tasks;
    private final GenerationRevisionService revisions;
    private final GenerationChangePlanMapper plans;
    private final GenerationSelectionMapper selections;
    private final GenerationModel model;
    private final ModelConnectionService connections;
    private final TaskExecutor executor;
    public GenerationChangeService(GenerationTaskService tasks, GenerationRevisionService revisions,GenerationChangePlanMapper plans,GenerationSelectionMapper selections,GenerationModel model,ModelConnectionService connections,@Qualifier("generationTaskExecutor") TaskExecutor executor) { this.tasks=tasks;this.revisions=revisions;this.plans=plans;this.selections=selections;this.model=model;this.connections=connections;this.executor=executor; }
    public ChangePlanResponse plan(Long sid,Long revisionId,ChangeProjectRequest request) {
        var parent=revisions.ready(sid,revisionId);
        var old=plans.selectOne(Wrappers.lambdaQuery(GenerationChangePlan.class).eq(GenerationChangePlan::getRevisionId,revisionId).eq(GenerationChangePlan::getAttemptKey,request.attemptKey()));
        if(old!=null)return response(old);
        var connection=connections.requireActiveConnection();var plan=new GenerationChangePlan();plan.setSessionId(sid);plan.setRevisionId(revisionId);plan.setAttemptKey(request.attemptKey());plan.setRequestText(request.request().trim());plan.setStatus("PLANNING");plan.setRunningKey(sid);plan.setCreatedAt(LocalDateTime.now());plan.setUpdatedAt(plan.getCreatedAt());
        try { plans.insert(plan); } catch(DuplicateKeyException e) { throw new BusinessException(ErrorCode.CONFLICT,"已有修改计划正在整理，请等待完成"); }
        try { tasks.submit("PLAN",plan.getId(),sid,plan.getAttemptKey(),executor,()->{ try { GenerationTaskService.phase("AI 正在整理修改计划");var output=model.planChange(connection,revisions.bundle(parent),plan.getRequestText());if(output==null)throw new IllegalStateException();
            GenerationTaskService.publish(()->plans.update(null,active(plan,"PLANNING").set(GenerationChangePlan::getStatus,"PLANNED").set(GenerationChangePlan::getRunningKey,null).set(GenerationChangePlan::getPlanJson,revisions.encode(output)).set(GenerationChangePlan::getUpdatedAt,LocalDateTime.now())));
        } catch(Exception e) { fail(plan,"PLANNING","修改计划生成失败，原项目保持不变，请重试"); } }); }
        catch(RuntimeException e) { fail(plan,"PLANNING","队列已满，请稍后重试"); }
        return response(plans.selectById(plan.getId()));
    }
    public ChangePlanResponse latest(Long sid,Long revisionId) {
        revisions.owned(sid,revisionId);
        var p=plans.selectOne(Wrappers.lambdaQuery(GenerationChangePlan.class).eq(GenerationChangePlan::getRevisionId,revisionId).orderByDesc(GenerationChangePlan::getCreatedAt).orderByDesc(GenerationChangePlan::getId).last("LIMIT 1"));
        return p==null?null:get(sid,p.getId());
    }
    public ChangePlanResponse get(Long sid,Long id) {
        var p=owned(sid,id);
        if((p.getStatus().equals("PLANNING")||p.getStatus().equals("GENERATING"))&&p.getUpdatedAt().isBefore(LocalDateTime.now().minusMinutes(10))) {if(p.getResultRevisionId()!=null)revisions.get(sid,p.getResultRevisionId());fail(p,p.getStatus(),"修改任务已中断，请重新创建计划");p=owned(sid,id);}
        return response(p);
    }
    public ChangePlanResponse approve(Long sid,Long id) {
        var p=owned(sid,id);
        if(!p.getStatus().equals("PLANNED"))return response(p);
        var parent=revisions.ready(sid,p.getRevisionId());var connection=connections.requireActiveConnection();
        int changed=plans.update(null,active(p,"PLANNED").set(GenerationChangePlan::getStatus,"GENERATING").set(GenerationChangePlan::getUpdatedAt,LocalDateTime.now()));
        if(changed!=1)return get(sid,id);
        GenerationRevision target;
        try { target=revisions.prepareChange(parent,p.getRequestText(),"change-"+p.getId()); }
        catch(RuntimeException e) { fail(p,"GENERATING","已有任务处理中或版本达到上限，请稍后重新创建计划");throw e; }
        plans.update(null,active(p,"GENERATING").set(GenerationChangePlan::getResultRevisionId,target.getId()));
        try { tasks.submit("CHANGE",p.getId(),sid,p.getAttemptKey(),executor,()->{try {
            GenerationTaskService.phase("AI 正在按确认计划修改源码");var output=model.change(connection,revisions.bundle(parent),p.getRequestText(),revisions.decode(p.getPlanJson(),new com.fasterxml.jackson.core.type.TypeReference<ProjectChangePlan>(){}));
            GenerationTaskService.phase("检查并保存独立版本");revisions.complete(target,output);
            var success=revisions.completed(target);
            plans.update(null,active(p,"GENERATING").set(GenerationChangePlan::getStatus,success?"SUCCEEDED":"FAILED").set(GenerationChangePlan::getUpdatedAt,LocalDateTime.now()));
        }catch(Exception e){revisions.fail(target,"修改生成失败，原源码和旧版本保留");fail(p,"GENERATING","修改生成失败，原项目保持不变");}}); }
        catch(RuntimeException e){revisions.fail(target,"队列已满，请稍后重试");fail(p,"GENERATING","队列已满，请稍后重试");}
        return get(sid,id);
    }
    public RevisionResponse selected(Long sid,Long runId) {var root=revisions.root(sid,runId);var saved=selections.selectById(runId);return saved==null?root:revisions.get(sid,saved.getRevisionId());}
    public RevisionResponse select(Long sid,Long runId,Long id) {
        var r=revisions.ready(sid,id);if(!r.getRunId().equals(runId))throw new BusinessException(ErrorCode.INVALID_ARGUMENT,"此版本不属于该项目");
        var selection=new GenerationSelection();selection.setRunId(runId);selection.setRevisionId(id);selection.setUpdatedAt(LocalDateTime.now());
        if(selections.updateById(selection)==0)try{selections.insert(selection);}catch(DuplicateKeyException e){selections.updateById(selection);}
        return revisions.response(r);
    }
    private GenerationChangePlan owned(Long sid,Long id) {var p=plans.selectById(id);if(p==null) {revisions.get(sid,-1L);throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,"修改计划不存在");}revisions.owned(sid,p.getRevisionId());return p;}
    private com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<GenerationChangePlan> active(GenerationChangePlan p,String status) {return Wrappers.lambdaUpdate(GenerationChangePlan.class).eq(GenerationChangePlan::getId,p.getId()).eq(GenerationChangePlan::getStatus,status);}
    private void fail(GenerationChangePlan p,String status,String error){plans.update(null,active(p,status).set(GenerationChangePlan::getStatus,"FAILED").set(GenerationChangePlan::getRunningKey,null).set(GenerationChangePlan::getErrorMessage,error).set(GenerationChangePlan::getUpdatedAt,LocalDateTime.now()));}
    private ChangePlanResponse response(GenerationChangePlan p) {return new ChangePlanResponse(p.getId().toString(),p.getRevisionId().toString(),p.getRequestText(),p.getStatus(),p.getPlanJson()==null?null:revisions.decode(p.getPlanJson(),new com.fasterxml.jackson.core.type.TypeReference<ProjectChangePlan>(){}),p.getResultRevisionId()==null?null:p.getResultRevisionId().toString(),p.getErrorMessage());}
}
