package com.devmate.generation.service;

import com.devmate.agent.model.ModelConnectionSnapshot;
import com.devmate.agent.service.ModelConnectionService;
import com.devmate.generation.model.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Files;
import java.util.*;
import java.io.ByteArrayInputStream;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ProjectGenerationFlowTest.RealProvider.class)
class ProjectGenerationFlowTest {
    @TestConfiguration
    static class RealProvider {
        @Bean @Primary RequirementDraftProvider aiDrafts(GenerationModel model) { return new AiRequirementDraftProvider(model); }
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean GenerationModel model;
    @MockitoBean GenerationSandbox sandbox;
    @MockitoBean ModelConnectionService connections;
    @MockitoBean(name = "generationTaskExecutor") TaskExecutor executor;
    static final String ATTEMPT = "test-attempt-00000001";
    static final String ROOT;
    static { try { ROOT = Files.createTempDirectory("devmate-generated-test-").toRealPath().toString(); } catch (Exception e) { throw new RuntimeException(e); } }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) { registry.add("devmate.generation.workspace-root", () -> ROOT); }

    @BeforeEach void setup() {
        when(connections.requireActiveConnection()).thenReturn(new ModelConnectionSnapshot("COMPATIBLE", "test-model", "https://model.example/v1", "test-key"));
        when(model.draft(anyString(), any(), anyList())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            RequirementDraft previous = invocation.getArgument(1);
            List<RequirementQuestion> questions = new ArrayList<>(previous == null ? List.of() : previous.questions());
            String stage = previous == null ? "BUSINESS" : switch(previous.stage()) {
                case "BUSINESS" -> "LANGUAGE"; case "LANGUAGE" -> "TECHNOLOGY"; case "TECHNOLOGY" -> "DETAILS"; default -> "REVIEW";
            };
            if (!stage.equals("REVIEW")) questions.add(question("q" + (questions.size()+1), stage));
            return new RequirementDraft("个人待办工具，新增、列出和完成待办", "Python / 标准库 / 单进程CLI / JSON文件", List.of("只在本地使用"), questions, "project-builder-v1", stage, stage.equals("REVIEW"));
        });
        when(model.generate(any(), any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return bundle();
        });
        doAnswer(invocation -> { invocation.<Runnable>getArgument(0).run(); return null; }).when(executor).execute(any(Runnable.class));
    }
    static RequirementQuestion question(String id, String stage) {
        return new RequirementQuestion(id, RequirementQuestionCategory.TECHNICAL, RequirementInputType.SINGLE_CHOICE,
                stage + "选择", "决定实现方式", "Python", "容易开始", List.of(new RequirementOption("a", "推荐方案", "易上手", "保持简单", true), new RequirementOption("b", "其他方案", "另一种选择", "增加配置", false)), true, true, false);
    }
    static ProjectBundle bundle() {
        return new ProjectBundle("todo", "本地待办", "python main.py，运行 python -m unittest 验证", List.of(
                new ProjectBundle.ProjectFile("README.md", "# 待办\n使用 python main.py\n验证 python -m unittest"),
                new ProjectBundle.ProjectFile("main.py", "print('Todo CLI')\n")), List.of("未执行代码"));
    }
    JsonNode create() throws Exception {
        return data(mvc.perform(post("/api/generation-sessions").contentType(MediaType.APPLICATION_JSON).content("{\"requirement\":\"做一个Python个人待办CLI\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    JsonNode next(JsonNode session) throws Exception {
        Set<String> answered = new HashSet<>(); session.at("/latestSpec/answers").forEach(answer -> answered.add(answer.path("questionId").asText()));
        var body = json.createObjectNode(); body.put("versionId", session.at("/latestSpec/id").asText());
        var answers = body.putArray("answers");
        session.at("/latestSpec/questions").forEach(q -> { if (!answered.contains(q.path("id").asText())) answers.addObject().put("questionId", q.path("id").asText()).put("decisionMode", "AI_DEFAULTED"); });
        return data(mvc.perform(post("/api/generation-sessions/{id}/clarifications", session.path("id").asText()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    JsonNode confirmed() throws Exception {
        JsonNode s = create(); for(int i=0;i<4;i++) s=next(s);
        assertThat(s.at("/latestSpec/questions").size()).isEqualTo(4);
        assertThat(s.at("/latestSpec/answers").size()).isEqualTo(4);
        assertThat(s.at("/latestSpec/readyForGeneration").asBoolean()).isTrue();
        return data(mvc.perform(post("/api/generation-sessions/{id}/confirmations",s.path("id").asText()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"versionId\":\""+s.at("/latestSpec/id").asText()+"\"}" )).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    JsonNode start(JsonNode s,String attempt) throws Exception {
        return data(mvc.perform(post("/api/generation-sessions/{id}/runs",s.path("id").asText()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"versionId\":\""+s.at("/latestSpec/id").asText()+"\",\"attemptKey\":\""+attempt+"\"}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString());
    }
    JsonNode data(String body) throws Exception { return json.readTree(body).path("data"); }

    @Test void followsMultipleRoundsThenGeneratesReadsDownloadsAndReusesSuccess() throws Exception {
        JsonNode s=confirmed(), run=start(s,ATTEMPT); String sid=s.path("id").asText(), rid=run.path("id").asText();
        assertThat(run.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(run.path("verification").asText()).isEqualTo("STRUCTURE_ONLY");
        mvc.perform(get("/api/generation-sessions/{id}/runs/{run}/files",sid,rid).param("path","main.py"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content").value("print('Todo CLI')\n"));
        byte[] zip=mvc.perform(get("/api/generation-sessions/{id}/runs/{run}/download",sid,rid)).andExpect(status().isOk())
                .andExpect(content().contentType("application/zip")).andReturn().getResponse().getContentAsByteArray();
        List<String> paths=new ArrayList<>(); try(var input=new ZipInputStream(new ByteArrayInputStream(zip))) { java.util.zip.ZipEntry entry; while((entry=input.getNextEntry())!=null) paths.add(entry.getName()); }
        assertThat(paths).containsExactly("README.md","main.py","DEVMATE_GENERATION.md");
        assertThat(start(s,ATTEMPT).path("id").asText()).isEqualTo(rid);
        assertThat(start(s,"test-attempt-00000002").path("id").asText()).isEqualTo(rid);
        verify(model,times(1)).generate(any(),any());
        mvc.perform(get("/api/generation-sessions/{id}/runs/{run}/files",sid,rid).param("path","../secret" )).andExpect(status().isNotFound());
        mvc.perform(get("/api/generation-sessions")).andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray());
    }
    @Test void rejectsEarlyConfirmationInvalidSelectionsAndStaleVersionBeforeModelCall() throws Exception {
        JsonNode s=create(); String sid=s.path("id").asText();
        mvc.perform(post("/api/generation-sessions/{id}/confirmations",sid).contentType(MediaType.APPLICATION_JSON).content("{\"versionId\":\""+s.at("/latestSpec/id").asText()+"\"}" )).andExpect(status().isConflict());
        mvc.perform(post("/api/generation-sessions/{id}/clarifications",sid).contentType(MediaType.APPLICATION_JSON).content("{\"versionId\":\"1\",\"answers\":[{\"questionId\":\"q1\",\"decisionMode\":\"AI_DEFAULTED\"}]}" )).andExpect(status().isConflict());
        mvc.perform(post("/api/generation-sessions/{id}/clarifications",sid).contentType(MediaType.APPLICATION_JSON).content("{\"answers\":[{\"questionId\":\"q1\",\"decisionMode\":\"USER_SELECTED\",\"selectedOptionIds\":[\"unknown\"]}]}" )).andExpect(status().isBadRequest());
        verify(model,times(1)).draft(anyString(),any(),anyList());
        mvc.perform(post("/api/generation-sessions/{id}/runs",sid).contentType(MediaType.APPLICATION_JSON).content("{\"versionId\":\"1\",\"attemptKey\":\""+ATTEMPT+"\"}" )).andExpect(status().isConflict());
    }
    @Test void restoresClarifyingStateAfterModelFailureAndPreservesPreviousVersion() throws Exception {
        JsonNode s=create(); when(model.draft(anyString(),notNull(),anyList())).thenThrow(new com.devmate.common.error.BusinessException(com.devmate.common.error.ErrorCode.INTERNAL_ERROR,"AI超时"));
        mvc.perform(post("/api/generation-sessions/{id}/clarifications",s.path("id").asText()).contentType(MediaType.APPLICATION_JSON).content("{\"answers\":[{\"questionId\":\"q1\",\"decisionMode\":\"AI_DEFAULTED\"}]}" )).andExpect(status().isInternalServerError());
        mvc.perform(get("/api/generation-sessions/{id}",s.path("id").asText())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLARIFYING")).andExpect(jsonPath("$.data.latestVersionNo").value(1)).andExpect(jsonPath("$.data.lastError").isNotEmpty());
    }
    @Test void recordsInvalidArtifactsAsFailureAndRetriesOnlyWithANewAttempt() throws Exception {
        JsonNode s=confirmed(); when(model.generate(any(),any())).thenReturn(new ProjectBundle("bad","bad","run",List.of(new ProjectBundle.ProjectFile("README.md","hi"),new ProjectBundle.ProjectFile("../escape.py","bad")),List.of()));
        JsonNode run=start(s,ATTEMPT); assertThat(run.path("status").asText()).isEqualTo("FAILED");
        start(s,ATTEMPT); verify(model,times(1)).generate(any(),any());
        mvc.perform(get("/api/generation-sessions/{id}/runs/{run}/download",s.path("id").asText(),run.path("id").asText())).andExpect(status().isConflict());
        when(model.generate(any(),any())).thenReturn(bundle());
        assertThat(start(s,"test-attempt-00000002").path("status").asText()).isEqualTo("SUCCEEDED");
    }
    @Test void recordsExecutorRejectionAsRecoverableFailure() throws Exception {
        JsonNode s=confirmed(); doThrow(new org.springframework.core.task.TaskRejectedException("queue full")).when(executor).execute(any(Runnable.class));
        JsonNode run=start(s,ATTEMPT); assertThat(run.path("status").asText()).isEqualTo("FAILED"); assertThat(run.path("errorMessage").asText()).contains("队列"); verify(model,never()).generate(any(),any());
    }
    @Test void expiresAnInterruptedRunWithoutCallingTheModelAgain() throws Exception {
        JsonNode s=confirmed(); doNothing().when(executor).execute(any(Runnable.class));
        JsonNode run=start(s,ATTEMPT); assertThat(run.path("status").asText()).isEqualTo("RUNNING");
        jdbc.update("UPDATE generation_run SET updated_at=? WHERE id=?",java.time.LocalDateTime.now().minusMinutes(11),run.path("id").asLong());
        mvc.perform(get("/api/generation-sessions/{id}/runs/latest",s.path("id").asText())).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("FAILED")); verify(model,never()).generate(any(),any());
    }
    JsonNode root(JsonNode s,JsonNode run) throws Exception {
        return data(mvc.perform(post("/api/generation-sessions/{id}/runs/{run}/revisions",s.path("id").asText(),run.path("id").asText())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    JsonNode check(JsonNode s,JsonNode revision,int repairs,String key) throws Exception {
        return data(mvc.perform(post("/api/generation-sessions/{id}/revisions/{revision}/verifications",s.path("id").asText(),revision.path("id").asText()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"repairLimit\":"+repairs+",\"attemptKey\":\""+key+"\"}")).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString());
    }
    @Test void verifiesExactSnapshotAndPreservesOriginalWhenRepairSucceeds() throws Exception {
        JsonNode s=confirmed(),run=start(s,ATTEMPT),revision=root(s,run);
        when(sandbox.verify(any())).thenAnswer(invocation->{assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return new VerificationResult("FAILED","PYTHON_UNITTEST","python -m unittest",1,"assert failed",20,true);})
            .thenReturn(new VerificationResult("PASSED","PYTHON_UNITTEST","python -m unittest",0,"Ran 1 test",25,true));
        when(model.repair(any(),any(),any())).thenReturn(new ProjectBundle("todo","修复完成","run",List.of(new ProjectBundle.ProjectFile("README.md","# fixed"),new ProjectBundle.ProjectFile("main.py","print('Fixed')")),List.of()));
        JsonNode result=check(s,revision,1,"verify-attempt-00001");
        assertThat(result.path("status").asText()).isEqualTo("PASSED");assertThat(result.path("repairs").asInt()).isEqualTo(1);
        assertThat(result.path("resultRevisionId").asText()).isNotEqualTo(revision.path("id").asText());
        check(s,revision,1,"verify-attempt-00001");verify(model,times(1)).repair(any(),any(),any());
        mvc.perform(get("/api/generation-sessions/{id}/runs/{run}/files",s.path("id").asText(),run.path("id").asText()).param("path","main.py")).andExpect(status().isOk()).andExpect(jsonPath("$.data.content").value("print('Todo CLI')\n"));
        mvc.perform(get("/api/generation-sessions/{id}/revisions/{revision}/files",s.path("id").asText(),result.path("resultRevisionId").asText()).param("path","main.py")).andExpect(status().isOk()).andExpect(jsonPath("$.data.content").value("print('Fixed')"));
    }
    @Test void blockedVerificationDoesNotCallModelAndEnforcesRepairLimit() throws Exception {
        JsonNode s=confirmed(),revision=root(s,start(s,ATTEMPT));
        when(sandbox.verify(any())).thenReturn(new VerificationResult("BLOCKED","UNSUPPORTED","",null,"需要环境",0,false));
        assertThat(check(s,revision,2,"verify-attempt-00002").path("status").asText()).isEqualTo("BLOCKED");verify(model,never()).repair(any(),any(),any());
        mvc.perform(post("/api/generation-sessions/{id}/revisions/{rev}/verifications",s.path("id").asText(),revision.path("id").asText()).contentType(MediaType.APPLICATION_JSON).content("{\"repairLimit\":3,\"attemptKey\":\"verify-attempt-00003\"}")).andExpect(status().isBadRequest());
    }

    @Test void plansBeforeChangingThenComparesVersionsAndRestoresOriginal() throws Exception {
        var s=confirmed();var root=root(s,start(s,ATTEMPT));String sid=s.path("id").asText();
        when(model.planChange(any(),any(),anyString())).thenReturn(new ProjectChangePlan("增加搜索",List.of("修改main.py","补充README"),List.of("尚待运行验证")));
        when(model.change(any(),any(),anyString(),any())).thenAnswer(invocation->{assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return new ProjectBundle("todo","增加搜索","run",List.of(new ProjectBundle.ProjectFile("README.md","# 搜索待办"),new ProjectBundle.ProjectFile("main.py","print('search')"),new ProjectBundle.ProjectFile("search.py","def search(): return []")),List.of("未运行"));});
        String body="{\"request\":\"增加搜索\",\"attemptKey\":\"change-attempt-00001\"}";
        var plan=data(mvc.perform(post("/api/generation-sessions/{id}/revisions/{revision}/change-plans",sid,root.path("id").asText()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString());
        assertThat(plan.path("status").asText()).isEqualTo("PLANNED");verify(model,never()).change(any(),any(),anyString(),any());
        var generated=data(mvc.perform(post("/api/generation-sessions/{id}/change-plans/{plan}/confirmations",sid,plan.path("id").asText())).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString());
        assertThat(generated.path("status").asText()).isEqualTo("SUCCEEDED");String rid=generated.path("resultRevisionId").asText();
        mvc.perform(post("/api/generation-sessions/{id}/change-plans/{plan}/confirmations",sid,plan.path("id").asText())).andExpect(status().isAccepted());verify(model,times(1)).change(any(),any(),anyString(),any());
        mvc.perform(get("/api/generation-sessions/{id}/revisions/{revision}/comparison",sid,rid).param("beforeId",root.path("id").asText())).andExpect(status().isOk()).andExpect(jsonPath("$.data.files.length()").value(3));
        mvc.perform(get("/api/generation-sessions/{id}/revisions/{revision}/diff",sid,rid).param("beforeId",root.path("id").asText()).param("path","main.py")).andExpect(status().isOk()).andExpect(jsonPath("$.data.before").value("print('Todo CLI')\n")).andExpect(jsonPath("$.data.after").value("print('search')"));
        mvc.perform(post("/api/generation-sessions/{id}/runs/{run}/selection/{revision}",sid,root.path("runId").asText(),rid)).andExpect(status().isOk());
        mvc.perform(post("/api/generation-sessions/{id}/runs/{run}/selection/{revision}",sid,root.path("runId").asText(),root.path("id").asText())).andExpect(status().isOk());
        mvc.perform(get("/api/generation-sessions/{id}/runs/{run}/selection",sid,root.path("runId").asText())).andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(root.path("id").asText()));
    }
    @Test void failedChangePreservesSelectionAndOriginalFiles() throws Exception {
        var s=confirmed();var root=root(s,start(s,ATTEMPT));String sid=s.path("id").asText();
        when(model.planChange(any(),any(),anyString())).thenReturn(new ProjectChangePlan("修改",List.of("保留原功能"),List.of()));
        when(model.change(any(),any(),anyString(),any())).thenThrow(new RuntimeException("private downstream error"));
        var plan=data(mvc.perform(post("/api/generation-sessions/{id}/revisions/{r}/change-plans",sid,root.path("id").asText()).contentType(MediaType.APPLICATION_JSON).content("{\"request\":\"增加搜索\",\"attemptKey\":\"change-attempt-00002\"}")).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/generation-sessions/{id}/change-plans/{plan}/confirmations",sid,plan.path("id").asText())).andExpect(status().isAccepted()).andExpect(jsonPath("$.data.status").value("FAILED"));
        mvc.perform(get("/api/generation-sessions/{id}/runs/{run}/selection",sid,root.path("runId").asText())).andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(root.path("id").asText()));
    }

    @Test void savesLearningGuideForExactRevisionAndReadsWithoutCallingModelAgain() throws Exception {
        var s=confirmed();var root=root(s,start(s,ATTEMPT));String sid=s.path("id").asText(),rid=root.path("id").asText();
        when(model.learningGuide(any(),any())).thenReturn(new ProjectLearningGuide("个人待办",List.of(new ProjectLearningGuide.Module("命令行","处理用户输入",List.of("main.py"))),List.of(new ProjectLearningGuide.Step("启动","打印待办入口",List.of("main.py"))),List.of(new ProjectLearningGuide.Step("先读入口","查看处理流程",List.of("main.py"))),List.of("静态导览，未执行")));
        String body="{\"attemptKey\":\"guide-attempt-00001\"}";
        mvc.perform(post("/api/generation-sessions/{id}/revisions/{rev}/learning-guides",sid,rid).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isAccepted()).andExpect(jsonPath("$.data.status").value("SUCCEEDED")).andExpect(jsonPath("$.data.revisionId").value(rid));
        mvc.perform(get("/api/generation-sessions/{id}/revisions/{rev}/learning-guides/latest",sid,rid)).andExpect(status().isOk()).andExpect(jsonPath("$.data.content.purpose").value("个人待办"));
        mvc.perform(post("/api/generation-sessions/{id}/revisions/{rev}/learning-guides",sid,rid).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isAccepted());verify(model,times(1)).learningGuide(any(),any());
    }
    @Test void rejectsInventedGuideReferencesRatherThanSavingFakeEvidence() throws Exception {
        var s=confirmed();var root=root(s,start(s,ATTEMPT));String sid=s.path("id").asText(),rid=root.path("id").asText();
        when(model.learningGuide(any(),any())).thenReturn(new ProjectLearningGuide("不存在的功能",List.of(new ProjectLearningGuide.Module("支付","支付",List.of("missing.py"))),List.of(new ProjectLearningGuide.Step("支付","支付",List.of("main.py"))),List.of(new ProjectLearningGuide.Step("支付","支付",List.of("main.py"))),List.of()));
        mvc.perform(post("/api/generation-sessions/{id}/revisions/{rev}/learning-guides",sid,rid).contentType(MediaType.APPLICATION_JSON).content("{\"attemptKey\":\"guide-attempt-00002\"}")).andExpect(status().isAccepted()).andExpect(jsonPath("$.data.status").value("FAILED")).andExpect(jsonPath("$.data.content").doesNotExist());
    }

    @Test void explainsOnlySanitizedErrorForExactRevisionAndKeepsRuntimeGuideReadOnly() throws Exception {
        var s=confirmed();var root=root(s,start(s,ATTEMPT));String sid=s.path("id").asText(),rid=root.path("id").asText();
        when(model.explainError(any(),any(),anyString())).thenAnswer(invocation->{assertThat(invocation.<String>getArgument(2)).doesNotContain("private-pass");return new ProjectErrorHelp("缺少依赖",List.of("尚未安装"),List.of(new ProjectErrorHelp.Action("检查环境","在自己的终端确认版本","python3 --version")),List.of("仅建议，尚未执行"));});
        mvc.perform(get("/api/generation-sessions/{id}/revisions/{rev}/runtime-guide",sid,rid)).andExpect(status().isOk()).andExpect(jsonPath("$.data.language").value("Python"));verify(model,never()).explainError(any(),any(),anyString());
        mvc.perform(post("/api/generation-sessions/{id}/revisions/{rev}/error-help",sid,rid).contentType(MediaType.APPLICATION_JSON).content("{\"error\":\"password=private-pass ModuleNotFoundError\",\"attemptKey\":\"error-attempt-00001\"}")).andExpect(status().isAccepted()).andExpect(jsonPath("$.data.status").value("SUCCEEDED"));
        String stored=jdbc.queryForObject("SELECT request_text FROM generation_insight WHERE revision_id=? AND kind='ERROR_HELP'",String.class,Long.valueOf(rid));assertThat(stored).doesNotContain("private-pass").contains("REDACTED");
    }

    JsonNode task(JsonNode s,JsonNode run) throws Exception {return data(mvc.perform(get("/api/generation-sessions/{id}/tasks/latest",s.path("id").asText()).param("kind","GENERATE").param("referenceId",run.path("id").asText())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
    @Test void cancelsQueuedGenerationWithoutCallingModelAndAllowsExplicitRetry() throws Exception {
        var s=confirmed();var queued=new java.util.concurrent.atomic.AtomicReference<Runnable>();doAnswer(invocation->{queued.set(invocation.getArgument(0));return null;}).when(executor).execute(any(Runnable.class));
        var run=start(s,ATTEMPT);var task=task(s,run);assertThat(task.path("status").asText()).isEqualTo("QUEUED");
        mvc.perform(post("/api/generation-sessions/{id}/tasks/{task}/cancellations",s.path("id").asText(),task.path("id").asText())).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"));
        queued.get().run();verify(model,never()).generate(any(),any());
        mvc.perform(get("/api/generation-sessions/{id}/runs/latest",s.path("id").asText())).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"));
        doAnswer(invocation->{invocation.<Runnable>getArgument(0).run();return null;}).when(executor).execute(any(Runnable.class));
        assertThat(start(s,"retry-after-cancel-00001").path("status").asText()).isEqualTo("SUCCEEDED");
    }
    @Test void cancelledRunningModelCannotPublishLateResult() throws Exception {
        var s=confirmed();var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);var worker=new java.util.concurrent.atomic.AtomicReference<Thread>();
        when(model.generate(any(),any())).thenAnswer(invocation->{entered.countDown();while(release.getCount()>0){try{release.await();}catch(InterruptedException ignored){}}return bundle();});
        doAnswer(invocation->{var thread=new Thread(invocation.getArgument(0,Runnable.class));worker.set(thread);thread.start();return null;}).when(executor).execute(any(Runnable.class));
        var run=start(s,ATTEMPT);assertThat(entered.await(3,java.util.concurrent.TimeUnit.SECONDS)).isTrue();var task=task(s,run);
        try{mvc.perform(post("/api/generation-sessions/{id}/tasks/{task}/cancellations",s.path("id").asText(),task.path("id").asText())).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"));}
        finally{release.countDown();worker.get().join(3000);}
        mvc.perform(get("/api/generation-sessions/{id}/runs/latest",s.path("id").asText())).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"));
        mvc.perform(get("/api/generation-sessions/{id}/runs/{run}/download",s.path("id").asText(),run.path("id").asText())).andExpect(status().isConflict());
    }
    @Test void recordsReportedTokenUsageAndKeepsMissingUsageUnknown() throws Exception {
        var s=confirmed();when(model.generate(any(),any())).thenAnswer(invocation->{GenerationTaskService.modelStarted();GenerationTaskService.usage(new org.springframework.ai.chat.metadata.DefaultUsage(13,7));return bundle();});var run=start(s,ATTEMPT);var known=task(s,run);
        assertThat(known.path("inputTokens").asInt()).isEqualTo(13);assertThat(known.path("outputTokens").asInt()).isEqualTo(7);assertThat(known.path("usageComplete").asBoolean()).isTrue();
        var another=confirmed();when(model.generate(any(),any())).thenAnswer(invocation->{GenerationTaskService.modelStarted();return bundle();});var unknown=task(another,start(another,ATTEMPT));
        assertThat(unknown.path("usageComplete").asBoolean()).isFalse();assertThat(unknown.has("inputTokens")).isFalse();assertThat(unknown.path("modelCalls").asInt()).isEqualTo(1);
    }

    @Test void supportsOriginalSnapshotWithLowercaseReadme() throws Exception {
        var s=confirmed();when(model.generate(any(),any())).thenReturn(new ProjectBundle("todo","待办","run",List.of(new ProjectBundle.ProjectFile("readme.md","# todo"),new ProjectBundle.ProjectFile("main.py","print('todo')")),List.of()));
        assertThat(root(s,start(s,ATTEMPT)).path("status").asText()).isEqualTo("SUCCEEDED");
    }

}
