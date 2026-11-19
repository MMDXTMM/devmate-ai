package com.devmate.generation.service;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devmate.agent.service.ModelConnectionService;
import com.devmate.common.error.*;
import com.devmate.generation.dto.*;
import com.devmate.generation.entity.*;
import com.devmate.generation.mapper.GenerationInsightMapper;
import com.devmate.generation.model.*;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DuplicateKeyException;
import java.time.LocalDateTime;
import java.util.*;
@Service
public class GenerationInsightService {
    private final GenerationTaskService tasks;
    private final GenerationRevisionService revisions;private final GenerationInsightMapper insights;private final GenerationModel model;
    private final ModelConnectionService connections;private final TaskExecutor executor;private final ObjectMapper json;
    public GenerationInsightService(GenerationTaskService tasks, GenerationRevisionService revisions,GenerationInsightMapper insights,GenerationModel model,ModelConnectionService connections,@Qualifier("generationTaskExecutor") TaskExecutor executor,ObjectMapper json) {this.tasks=tasks;this.revisions=revisions;this.insights=insights;this.model=model;this.connections=connections;this.executor=executor;this.json=json;}
    public InsightResponse errorHelp(Long sid,Long revisionId,ErrorHelpRequest request){return start(sid,revisionId,"ERROR_HELP",request.attemptKey(),DiagnosticRedactor.redact(request.error()));}
    public InsightResponse guide(Long sid,Long revisionId,InsightRequest request) {return start(sid,revisionId,"GUIDE",request.attemptKey(),null);}
    public InsightResponse latest(Long sid,Long revisionId,String kind) {
        revisions.owned(sid,revisionId);var i=insights.selectOne(Wrappers.lambdaQuery(GenerationInsight.class).eq(GenerationInsight::getRevisionId,revisionId).eq(GenerationInsight::getKind,kind).orderByDesc(GenerationInsight::getCreatedAt).orderByDesc(GenerationInsight::getId).last("LIMIT 1"));
        if(i!=null&&i.getStatus().equals("RUNNING")&&i.getUpdatedAt().isBefore(LocalDateTime.now().minusMinutes(10))) {fail(i,"上次分析已中断，请重新发起");i=insights.selectById(i.getId());}
        return i==null?null:response(i);
    }
    private InsightResponse start(Long sid,Long revisionId,String kind,String key,String input) {
        var revision=revisions.ready(sid,revisionId);
        var old=insights.selectOne(Wrappers.lambdaQuery(GenerationInsight.class).eq(GenerationInsight::getRevisionId,revisionId).eq(GenerationInsight::getKind,kind).eq(GenerationInsight::getAttemptKey,key));if(old!=null)return response(old);
        var connection=connections.requireActiveConnection();var i=new GenerationInsight();i.setSessionId(sid);i.setRevisionId(revisionId);i.setKind(kind);i.setAttemptKey(key);i.setRequestText(input);i.setRunningKey(revisionId+"-"+kind);i.setStatus("RUNNING");i.setCreatedAt(LocalDateTime.now());i.setUpdatedAt(i.getCreatedAt());
        try{insights.insert(i);}catch(DuplicateKeyException e){throw new BusinessException(ErrorCode.CONFLICT,"此版本已有分析进行中，请刷新进度");}
        try{tasks.submit(kind,i.getId(),sid,i.getAttemptKey(),executor,()->{try{GenerationTaskService.phase(kind.equals("GUIDE")?"AI 正在理解当前源码":"AI 正在解释脱敏报错");var project=revisions.bundle(revision);Object guide;
            if(kind.equals("GUIDE")){var learning=model.learningGuide(connection,project);validate(learning,project);guide=learning;}
            else {var help=model.explainError(connection,project,input);validateHelp(help);guide=help;}
            Object content=guide;GenerationTaskService.publish(()->insights.update(null,active(i).set(GenerationInsight::getStatus,"SUCCEEDED").set(GenerationInsight::getRunningKey,null).set(GenerationInsight::getOutputJson,revisions.encode(content)).set(GenerationInsight::getUpdatedAt,LocalDateTime.now())));
        }catch(Exception e){fail(i,e instanceof BusinessException?e.getMessage():"导览生成失败，源码未改变，请重试");}});}catch(RuntimeException e){fail(i,"分析队列已满，请稍后重试");}
        return response(insights.selectById(i.getId()));
    }
    static void validate(ProjectLearningGuide guide,ProjectBundle project) {
        if(guide==null||!text(guide.purpose(),6000)||guide.modules()==null||guide.modules().isEmpty()||guide.modules().size()>10||guide.flow()==null||guide.flow().isEmpty()||guide.flow().size()>12||guide.readingOrder()==null||guide.readingOrder().isEmpty()||guide.readingOrder().size()>12||guide.limits()==null||guide.limits().size()>10)throw invalid();
        var paths=new HashSet<String>();project.files().forEach(f->paths.add(f.path()));
        for(var module:guide.modules()){if(module==null||!text(module.name(),200)||!text(module.responsibility(),3000))throw invalid();paths(module.paths(),paths);}
        var steps=new ArrayList<>(guide.flow());steps.addAll(guide.readingOrder());for(var step:steps){if(step==null||!text(step.title(),300)||!text(step.explanation(),3000))throw invalid();paths(step.paths(),paths);}
        if(guide.limits().stream().anyMatch(s->!text(s,2000)))throw invalid();
    }
    static void validateHelp(ProjectErrorHelp help){
        if(help==null||!text(help.explanation(),6000)||help.possibleCauses()==null||help.possibleCauses().isEmpty()||help.possibleCauses().size()>8||help.steps()==null||help.steps().isEmpty()||help.steps().size()>10||help.limits()==null||help.limits().size()>8)throw invalid();
        for(var step:help.steps())if(step==null||!text(step.title(),300)||!text(step.instruction(),3000)||step.command()==null||step.command().length()>1500)throw invalid();
        if(help.possibleCauses().stream().anyMatch(s->!text(s,2000))||help.limits().stream().anyMatch(s->!text(s,2000)))throw invalid();
    }
    private static boolean text(String s,int max){return s!=null&&!s.isBlank()&&s.length()<=max;}
    private static void paths(List<String> values,Set<String> allowed){if(values==null||values.isEmpty()||values.size()>10||values.stream().anyMatch(v->!allowed.contains(v)))throw invalid();}
    private static BusinessException invalid(){return new BusinessException(ErrorCode.INVALID_ARGUMENT,"AI导览不完整或引用了不存在的文件，请重试；源码保持不变");}
    private com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<GenerationInsight> active(GenerationInsight i){return Wrappers.lambdaUpdate(GenerationInsight.class).eq(GenerationInsight::getId,i.getId()).eq(GenerationInsight::getStatus,"RUNNING");}
    private void fail(GenerationInsight i,String error){insights.update(null,active(i).set(GenerationInsight::getStatus,"FAILED").set(GenerationInsight::getRunningKey,null).set(GenerationInsight::getErrorMessage,error).set(GenerationInsight::getUpdatedAt,LocalDateTime.now()));}
    private InsightResponse response(GenerationInsight i){try{return new InsightResponse(i.getId().toString(),i.getRevisionId().toString(),i.getKind(),i.getStatus(),i.getOutputJson()==null?null:json.readTree(i.getOutputJson()),i.getErrorMessage(),i.getCreatedAt(),i.getUpdatedAt());}catch(Exception e){throw new IllegalStateException("分析记录无法读取",e);}}
}
