package com.devmate.generation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devmate.common.error.*;
import com.devmate.generation.dto.GenerationTaskResponse;
import com.devmate.generation.entity.*;
import com.devmate.generation.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.core.task.TaskExecutor;
import org.springframework.ai.chat.metadata.Usage;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@Service
public class GenerationTaskService {
    private final org.springframework.transaction.support.TransactionTemplate transactions;
    private final GenerationTaskMapper tasks;private final GenerationSessionService sessions;
    private final GenerationRunMapper runs;private final GenerationVerificationMapper checks;private final GenerationChangePlanMapper plans;
    private final GenerationRevisionMapper revisions;private final GenerationInsightMapper insights;
    private final ConcurrentHashMap<Long,Thread> threads=new ConcurrentHashMap<>();
    private record Context(GenerationTaskService service,Long id) { }
    private static final ThreadLocal<Boolean> KNOWN=new ThreadLocal<>();
    private static final ThreadLocal<Context> CURRENT=new ThreadLocal<>();
    public GenerationTaskService(org.springframework.transaction.PlatformTransactionManager transactionManager,GenerationTaskMapper tasks,GenerationSessionService sessions,GenerationRunMapper runs,GenerationVerificationMapper checks,GenerationChangePlanMapper plans,GenerationRevisionMapper revisions,GenerationInsightMapper insights){this.transactions=new org.springframework.transaction.support.TransactionTemplate(transactionManager);this.tasks=tasks;this.sessions=sessions;this.runs=runs;this.checks=checks;this.plans=plans;this.revisions=revisions;this.insights=insights;}
    public void submit(String kind,Long referenceId,Long sid,String attemptKey,TaskExecutor executor,Runnable work){
        var task=new GenerationTask();task.setKind(kind);task.setReferenceId(referenceId);task.setSessionId(sid);task.setAttemptKey(attemptKey);task.setStatus("QUEUED");task.setPhase("等待工作线程");task.setModelCalls(0);task.setInputTokens(0L);task.setOutputTokens(0L);task.setUsageComplete(1);task.setCreatedAt(LocalDateTime.now());task.setUpdatedAt(task.getCreatedAt());tasks.insert(task);
        try{executor.execute(()->{
            if(tasks.update(null,Wrappers.lambdaUpdate(GenerationTask.class).eq(GenerationTask::getId,task.getId()).eq(GenerationTask::getStatus,"QUEUED").set(GenerationTask::getStatus,"RUNNING").set(GenerationTask::getPhase,"开始处理").set(GenerationTask::getStartedAt,LocalDateTime.now()))!=1)return;
            threads.put(task.getId(),Thread.currentThread());CURRENT.set(new Context(this,task.getId()));KNOWN.set(true);
            try { checkCancelled();work.run();var status=businessStatus(kind,referenceId);finish(task,status); }
            catch(CancellationException e){finish(task,"CANCELLED");}
            catch(RuntimeException e){finish(task,"FAILED");throw e;}
            finally{KNOWN.remove();CURRENT.remove();threads.remove(task.getId());Thread.interrupted();}
        });}catch(RuntimeException e){finish(task,"FAILED");throw e;}
    }
    public static void phase(String phase){var c=CURRENT.get();if(c!=null){checkCancelled();c.service.tasks.update(null,Wrappers.lambdaUpdate(GenerationTask.class).eq(GenerationTask::getId,c.id).eq(GenerationTask::getStatus,"RUNNING").set(GenerationTask::getPhase,phase).set(GenerationTask::getUpdatedAt,LocalDateTime.now()));}}
    public static void checkCancelled(){var c=CURRENT.get();if(Thread.currentThread().isInterrupted()||c!=null&&"CANCELLED".equals(c.service.tasks.selectById(c.id).getStatus()))throw new CancellationException("任务已取消");}
    public static void modelStarted(){var c=CURRENT.get();if(c==null)return;checkCancelled();var t=c.service.tasks.selectById(c.id);c.service.tasks.update(null,Wrappers.lambdaUpdate(GenerationTask.class).eq(GenerationTask::getId,c.id).set(GenerationTask::getModelCalls,t.getModelCalls()+1).set(GenerationTask::getUsageComplete,0));}
    public static void usage(Usage usage){var c=CURRENT.get();if(c==null)return;var t=c.service.tasks.selectById(c.id);
        Integer input=usage==null?null:usage.getPromptTokens(),output=usage==null?null:usage.getCompletionTokens();boolean known=input!=null&&output!=null&&(input>0||output>0);
        boolean complete=Boolean.TRUE.equals(KNOWN.get())&&known;KNOWN.set(complete);
        c.service.tasks.update(null,Wrappers.lambdaUpdate(GenerationTask.class).eq(GenerationTask::getId,c.id)
            .set(GenerationTask::getInputTokens,t.getInputTokens()+(known?input:0)).set(GenerationTask::getOutputTokens,t.getOutputTokens()+(known?output:0))
            .set(GenerationTask::getUsageComplete,complete?1:0).set(GenerationTask::getUpdatedAt,LocalDateTime.now()));
    }
    public GenerationTaskResponse latest(Long sid,String kind,Long ref){sessions.getSession(sid);if(!Set.of("GENERATE","VERIFY","PLAN","CHANGE","GUIDE","ERROR_HELP").contains(kind))throw new BusinessException(ErrorCode.INVALID_ARGUMENT,"未知任务类型");
        var t=tasks.selectOne(Wrappers.lambdaQuery(GenerationTask.class).eq(GenerationTask::getSessionId,sid).eq(GenerationTask::getKind,kind).eq(GenerationTask::getReferenceId,ref).orderByDesc(GenerationTask::getCreatedAt).orderByDesc(GenerationTask::getId).last("LIMIT 1"));
        if(t!=null&&Set.of("RUNNING","QUEUED").contains(t.getStatus())&&t.getUpdatedAt().isBefore(LocalDateTime.now().minusMinutes(15))){cancel(sid,t.getId());t=tasks.selectById(t.getId());}
        return t==null?null:response(t);
    }
    public GenerationTaskResponse cancel(Long sid,Long taskId){
        sessions.getSession(sid);
        var result=transactions.execute(ignored->{
            var t=tasks.selectOne(Wrappers.lambdaQuery(GenerationTask.class).eq(GenerationTask::getId,taskId).last("FOR UPDATE"));
            if(t==null||!sid.equals(t.getSessionId()))throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,"任务不存在");
            if(Set.of("QUEUED","RUNNING").contains(t.getStatus())){
                var business=businessStatus(t.getKind(),t.getReferenceId());
                if(!Set.of("RUNNING","PLANNING","GENERATING").contains(business)){finish(t,business);return response(tasks.selectById(taskId));}
                tasks.update(null,Wrappers.lambdaUpdate(GenerationTask.class).eq(GenerationTask::getId,taskId).set(GenerationTask::getStatus,"CANCELLED").set(GenerationTask::getPhase,"已取消，保留已有成功版本").set(GenerationTask::getFinishedAt,LocalDateTime.now()));
                cancelBusiness(t);
            }
            return response(tasks.selectById(taskId));
        });
        if(result.status().equals("CANCELLED")){Thread thread=threads.get(taskId);if(thread!=null)thread.interrupt();}
        return result;
    }
    /** Serialize result publication with cancellation, with a database-only short transaction. */
    public static void publish(Runnable write){
        var c=CURRENT.get();if(c==null){write.run();return;}
        c.service.transactions.executeWithoutResult(ignored->{
            var t=c.service.tasks.selectOne(Wrappers.lambdaQuery(GenerationTask.class).eq(GenerationTask::getId,c.id).last("FOR UPDATE"));
            if(!t.getStatus().equals("RUNNING"))throw new CancellationException("任务已取消");
            write.run();
        });
    }
    private void cancelBusiness(GenerationTask t){Long id=t.getReferenceId();switch(t.getKind()){
        case "GENERATE"->runs.update(null,Wrappers.lambdaUpdate(GenerationRun.class).eq(GenerationRun::getId,id).eq(GenerationRun::getAttemptKey,t.getAttemptKey()).eq(GenerationRun::getStatus,"RUNNING").set(GenerationRun::getStatus,"CANCELLED").set(GenerationRun::getErrorMessage,"已取消，已确认方案仍保留").set(GenerationRun::getUpdatedAt,LocalDateTime.now()));
        case "VERIFY"->checks.update(null,Wrappers.lambdaUpdate(GenerationVerification.class).eq(GenerationVerification::getId,id).eq(GenerationVerification::getStatus,"RUNNING").set(GenerationVerification::getStatus,"CANCELLED").set(GenerationVerification::getRunningKey,null).set(GenerationVerification::getUpdatedAt,LocalDateTime.now()));
        case "PLAN","CHANGE"->{var p=plans.selectById(id);plans.update(null,Wrappers.lambdaUpdate(GenerationChangePlan.class).eq(GenerationChangePlan::getId,id).in(GenerationChangePlan::getStatus,"PLANNING","GENERATING").set(GenerationChangePlan::getStatus,"CANCELLED").set(GenerationChangePlan::getRunningKey,null).set(GenerationChangePlan::getUpdatedAt,LocalDateTime.now()));if(t.getKind().equals("CHANGE")&&p.getResultRevisionId()!=null)revisions.update(null,Wrappers.lambdaUpdate(GenerationRevision.class).eq(GenerationRevision::getId,p.getResultRevisionId()).eq(GenerationRevision::getStatus,"RUNNING").set(GenerationRevision::getStatus,"CANCELLED").set(GenerationRevision::getRunningKey,null).set(GenerationRevision::getUpdatedAt,LocalDateTime.now()));}
        default->insights.update(null,Wrappers.lambdaUpdate(GenerationInsight.class).eq(GenerationInsight::getId,id).eq(GenerationInsight::getStatus,"RUNNING").set(GenerationInsight::getStatus,"CANCELLED").set(GenerationInsight::getRunningKey,null).set(GenerationInsight::getUpdatedAt,LocalDateTime.now()));
    }}
    private String businessStatus(String kind,Long id){return switch(kind){case "GENERATE"->runs.selectById(id).getStatus();case "VERIFY"->checks.selectById(id).getStatus();case "PLAN","CHANGE"->plans.selectById(id).getStatus();default->insights.selectById(id).getStatus();};}
    private void finish(GenerationTask task,String status){String finalStatus=Set.of("SUCCEEDED","PASSED","PLANNED").contains(status)?"SUCCEEDED":status.equals("BLOCKED")?"BLOCKED":status.equals("CANCELLED")?"CANCELLED":"FAILED";
        tasks.update(null,Wrappers.lambdaUpdate(GenerationTask.class).eq(GenerationTask::getId,task.getId()).in(GenerationTask::getStatus,"RUNNING","QUEUED").set(GenerationTask::getStatus,finalStatus).set(GenerationTask::getPhase,finalStatus.equals("SUCCEEDED")?"已完成":finalStatus.equals("BLOCKED")?"环境或验证条件不足":"处理结束，请查看结果说明").set(GenerationTask::getFinishedAt,LocalDateTime.now()).set(GenerationTask::getUpdatedAt,LocalDateTime.now()));}
    private GenerationTaskResponse response(GenerationTask t){boolean known=t.getUsageComplete()==1;var end=t.getFinishedAt()==null?LocalDateTime.now():t.getFinishedAt();return new GenerationTaskResponse(t.getId().toString(),t.getKind(),t.getReferenceId().toString(),t.getStatus(),t.getPhase(),t.getModelCalls(),known?t.getInputTokens():null,known?t.getOutputTokens():null,known,Math.max(0,Duration.between(t.getCreatedAt(),end).toMillis()),t.getCreatedAt(),t.getStartedAt(),t.getFinishedAt());}
}
