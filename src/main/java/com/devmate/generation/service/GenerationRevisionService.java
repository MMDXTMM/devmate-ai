package com.devmate.generation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devmate.common.error.*;
import com.devmate.generation.dto.*;
import com.devmate.generation.entity.*;
import com.devmate.generation.mapper.*;
import com.devmate.generation.model.ProjectBundle;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class GenerationRevisionService {
    private final GenerationSessionService sessions;
    private final ProjectGenerationService projects;
    private final GenerationRunMapper runs;
    private final GenerationRevisionMapper revisions;
    private final GeneratedProjectStore store;
    private final ObjectMapper json;
    public GenerationRevisionService(GenerationSessionService sessions, ProjectGenerationService projects,
            GenerationRunMapper runs, GenerationRevisionMapper revisions, GeneratedProjectStore store, ObjectMapper json) {
        this.sessions=sessions; this.projects=projects; this.runs=runs; this.revisions=revisions; this.store=store; this.json=json;
    }
    public RevisionResponse root(Long sessionId, Long runId) {
        projects.download(sessionId,runId); // verifies ownership, readiness and original artifact availability
        var existing=revisions.selectOne(Wrappers.lambdaQuery(GenerationRevision.class).eq(GenerationRevision::getRootRunId,runId));
        if(existing!=null) return response(existing);
        var run=runs.selectById(runId);
        var revision=new GenerationRevision(); revision.setSessionId(sessionId); revision.setRunId(runId); revision.setRootRunId(runId);
        revision.setAttemptKey("original-"+runId); revision.setKind("ORIGINAL"); revision.setStatus("SUCCEEDED");
        revision.setSummary(run.getSummary()); revision.setRunInstructions(run.getRunInstructions());
        revision.setManifestJson(run.getManifestJson()); revision.setWarningsJson(run.getWarningsJson());
        revision.setCreatedAt(LocalDateTime.now()); revision.setUpdatedAt(revision.getCreatedAt());
        try { revisions.insert(revision); } catch(DuplicateKeyException duplicate) {
            return response(revisions.selectOne(Wrappers.lambdaQuery(GenerationRevision.class).eq(GenerationRevision::getRootRunId,runId)));
        }
        return response(revision);
    }
    public GenerationRevision owned(Long sessionId,Long id) {
        sessions.getSession(sessionId);
        var revision=revisions.selectById(id);
        if(revision==null || !sessionId.equals(revision.getSessionId())) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,"源码版本不存在");
        return revision;
    }
    public GenerationRevision ready(Long sessionId,Long id) {
        var revision=owned(sessionId,id);
        if(!"SUCCEEDED".equals(revision.getStatus())) throw new BusinessException(ErrorCode.CONFLICT,"源码版本尚未完成");
        return revision;
    }
    public RevisionResponse get(Long sessionId,Long id) {
        var r=owned(sessionId,id);
        if("RUNNING".equals(r.getStatus())&&r.getUpdatedAt().isBefore(LocalDateTime.now().minusMinutes(10))) {fail(r,"修改任务已中断，请重新创建计划");r=owned(sessionId,id);}
        return response(r);
    }
    public boolean completed(GenerationRevision r) { return "SUCCEEDED".equals(revisions.selectById(r.getId()).getStatus()); }
    public ProjectBundle bundle(GenerationRevision revision) {
        var files=manifest(revision).stream().filter(f->!f.path().equals("DEVMATE_GENERATION.md")).map(f-> {
            try { return new ProjectBundle.ProjectFile(f.path(),store.read(storageRun(revision),f.path())); }
            catch(IOException e) { throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,"源码文件已不可用"); }
        }).toList();
        return new ProjectBundle("project",revision.getSummary(),revision.getRunInstructions(),files,decode(revision.getWarningsJson(),new TypeReference<>(){}));
    }
    public GeneratedFileResponse file(Long sid,Long id,String path) {
        var r=ready(sid,id);
        if(manifest(r).stream().noneMatch(f->f.path().equals(path))) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,"文件不存在");
        try { return new GeneratedFileResponse(path,store.read(storageRun(r),path)); }
        catch(IOException e) { throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,"文件已不可用"); }
    }
    public byte[] download(Long sid,Long id) {
        try { return store.download(storageRun(ready(sid,id))); }
        catch(IOException e) { throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,"源码包已不可用"); }
    }
    public GenerationRevision saveRepair(GenerationRevision parent,ProjectBundle bundle,String key) {
        if(revisions.selectCount(Wrappers.lambdaQuery(GenerationRevision.class).eq(GenerationRevision::getRunId,parent.getRunId()))>=50) throw new BusinessException(ErrorCode.CONFLICT,"此项目已达到50个源码版本上限");
        GenerationTaskService.checkCancelled();
        var r=new GenerationRevision(); r.setSessionId(parent.getSessionId()); r.setRunId(parent.getRunId()); r.setParentId(parent.getId());
        r.setAttemptKey(key); r.setKind("REPAIR"); r.setStatus("RUNNING"); r.setManifestJson("[]"); r.setWarningsJson("[]");
        r.setCreatedAt(LocalDateTime.now()); r.setUpdatedAt(r.getCreatedAt()); revisions.insert(r);
        try {
            var files=store.write(storageRun(r),bundle); r.setStatus("SUCCEEDED"); r.setManifestJson(encode(files));
            r.setSummary(bundle.summary()); r.setRunInstructions(bundle.runInstructions()); r.setWarningsJson(encode(bundle.warnings()));
        } catch(Exception e) { r.setStatus("FAILED"); r.setErrorMessage("修复产物未通过文件校验或无法保存"); }
        r.setUpdatedAt(LocalDateTime.now()); GenerationTaskService.publish(()->revisions.updateById(r));
        if(!"SUCCEEDED".equals(r.getStatus())) throw new BusinessException(ErrorCode.INTERNAL_ERROR,r.getErrorMessage());
        return r;
    }
    public List<RevisionResponse> list(Long sid,Long runId) {
        root(sid,runId);
        return revisions.selectList(Wrappers.lambdaQuery(GenerationRevision.class).eq(GenerationRevision::getRunId,runId).orderByDesc(GenerationRevision::getCreatedAt).orderByDesc(GenerationRevision::getId).last("LIMIT 50")).stream().map(this::response).toList();
    }
    public GenerationRevision prepareChange(GenerationRevision parent,String request,String key) {
        if(revisions.selectCount(Wrappers.lambdaQuery(GenerationRevision.class).eq(GenerationRevision::getRunId,parent.getRunId()))>=50) throw new BusinessException(ErrorCode.CONFLICT,"此项目已达到50个源码版本上限，请下载保存后新建项目");
        var r=new GenerationRevision(); r.setSessionId(parent.getSessionId());r.setRunId(parent.getRunId());r.setParentId(parent.getId());r.setAttemptKey(key);r.setRequestText(request);
        r.setKind("CHANGE");r.setStatus("RUNNING");r.setRunningKey(parent.getSessionId());r.setManifestJson("[]");r.setWarningsJson("[]");r.setCreatedAt(LocalDateTime.now());r.setUpdatedAt(r.getCreatedAt());
        try { revisions.insert(r); } catch(DuplicateKeyException duplicate) { throw new BusinessException(ErrorCode.CONFLICT,"已有修改正在生成，请等待完成"); } return r;
    }
    public void complete(GenerationRevision r,ProjectBundle bundle) {
        try {
            var files=store.write(storageRun(r),bundle);
            GenerationTaskService.publish(()->revisions.update(null,Wrappers.lambdaUpdate(GenerationRevision.class).eq(GenerationRevision::getId,r.getId()).eq(GenerationRevision::getStatus,"RUNNING")
                .set(GenerationRevision::getStatus,"SUCCEEDED").set(GenerationRevision::getRunningKey,null).set(GenerationRevision::getSummary,bundle.summary())
                .set(GenerationRevision::getRunInstructions,bundle.runInstructions()).set(GenerationRevision::getManifestJson,encode(files)).set(GenerationRevision::getWarningsJson,encode(bundle.warnings())).set(GenerationRevision::getUpdatedAt,LocalDateTime.now())));
        } catch(Exception e) { fail(r,"修改后的源码未通过文件校验或保存失败，旧版本保留"); }
    }
    public void fail(GenerationRevision r,String message) { revisions.update(null,Wrappers.lambdaUpdate(GenerationRevision.class).eq(GenerationRevision::getId,r.getId()).eq(GenerationRevision::getStatus,"RUNNING").set(GenerationRevision::getStatus,"FAILED").set(GenerationRevision::getRunningKey,null).set(GenerationRevision::getErrorMessage,message).set(GenerationRevision::getUpdatedAt,LocalDateTime.now())); }
    public RevisionComparison compare(Long sid,Long beforeId,Long afterId) {
        var before=ready(sid,beforeId);var after=ready(sid,afterId);if(!before.getRunId().equals(after.getRunId())) throw new BusinessException(ErrorCode.INVALID_ARGUMENT,"只能比较同一项目的版本");
        var a=new TreeMap<String,String>();var b=new TreeMap<String,String>();bundle(before).files().forEach(f->a.put(f.path(),f.content()));bundle(after).files().forEach(f->b.put(f.path(),f.content()));
        var names=new TreeSet<>(a.keySet());names.addAll(b.keySet());var result=new ArrayList<RevisionComparison.FileChange>();
        for(String path:names) if(!Objects.equals(a.get(path),b.get(path))) result.add(new RevisionComparison.FileChange(path,!a.containsKey(path)?"ADDED":!b.containsKey(path)?"DELETED":"MODIFIED"));
        return new RevisionComparison(beforeId.toString(),afterId.toString(),result);
    }
    public RevisionComparison.FileDiff diff(Long sid,Long before,Long after,String path) {
        var comparison=compare(sid,before,after);
        if(comparison.files().stream().noneMatch(f->f.path().equals(path))) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,"该文件没有差异");
        String a=manifest(ready(sid,before)).stream().anyMatch(f->f.path().equals(path))?file(sid,before,path).content():"";
        String b=manifest(ready(sid,after)).stream().anyMatch(f->f.path().equals(path))?file(sid,after,path).content():"";
        return new RevisionComparison.FileDiff(path,a,b);
    }
    private GenerationRun storageRun(GenerationRevision r) {
        if(r.getRootRunId()!=null) return runs.selectById(r.getRunId());
        var run=new GenerationRun(); run.setId(r.getRunId()); run.setSessionId(r.getSessionId()); run.setAttemptKey("revision-"+r.getId()); return run;
    }
    public List<GenerationRunResponse.FileEntry> manifest(GenerationRevision r) { return decode(r.getManifestJson(),new TypeReference<>(){}); }
    public RevisionResponse response(GenerationRevision r) { return new RevisionResponse(r.getId().toString(),r.getRunId().toString(),r.getParentId()==null?null:r.getParentId().toString(),r.getKind(),r.getStatus(),r.getRequestText(),r.getSummary(),r.getRunInstructions(),manifest(r),decode(r.getWarningsJson(),new TypeReference<>(){}),r.getErrorMessage(),r.getCreatedAt(),r.getUpdatedAt()); }
    public String encode(Object value) { try { return json.writeValueAsString(value); } catch(Exception e) { throw new IllegalStateException("版本记录无法编码",e); } }
    public <T> T decode(String value,TypeReference<T> type) { try { return json.readValue(value,type); } catch(Exception e) { throw new IllegalStateException("版本记录无法读取",e); } }
}
