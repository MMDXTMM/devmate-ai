package com.devmate.conversation.service;

import com.devmate.agent.model.ProjectConversationModel;
import com.devmate.agent.model.ProjectConversationModelRegistry;
import com.devmate.agent.model.ProjectConversationPrompt;
import com.devmate.agent.model.AiReviewException;
import com.devmate.common.error.BusinessException;
import com.devmate.conversation.dto.CreateConversationMessageRequest;
import com.devmate.conversation.dto.CreateConversationRequest;
import com.devmate.conversation.entity.ConversationMessage;
import com.devmate.conversation.mapper.ConversationMessageMapper;
import com.devmate.knowledge.dto.RetrievalHitResponse;
import com.devmate.knowledge.dto.RetrievalSearchResponse;
import com.devmate.knowledge.entity.KnowledgeChunk;
import com.devmate.knowledge.entity.KnowledgeDocument;
import com.devmate.knowledge.mapper.KnowledgeChunkMapper;
import com.devmate.knowledge.mapper.KnowledgeDocumentMapper;
import com.devmate.knowledge.retrieval.ContextRetrievalService;
import com.devmate.knowledge.retrieval.RetrievalSearchCommand;
import com.devmate.knowledge.retrieval.RetrievalMode;
import com.devmate.knowledge.service.ProjectUnderstandingReportService;
import com.devmate.project.dto.ProjectResponse;
import com.devmate.project.entity.Project;
import com.devmate.project.mapper.ProjectMapper;
import com.devmate.project.service.ProjectService;
import com.devmate.user.entity.AppUser;
import com.devmate.user.mapper.AppUserMapper;
import com.devmate.user.security.AuthenticatedUser;
import com.devmate.user.service.CurrentUserService;
import com.devmate.user.service.ProjectAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProjectConversationServiceTest {
    private static final long USER_ID = 9501L;
    private static final String REVISION = "cccccccccccccccccccccccccccccccccccccccc";

    @Autowired private ProjectConversationService service;
    @Autowired private AppUserMapper userMapper;
    @Autowired private ProjectMapper projectMapper;
    @Autowired private KnowledgeDocumentMapper documentMapper;
    @Autowired private KnowledgeChunkMapper chunkMapper;
    @Autowired private ConversationMessageMapper messageMapper;
    @Autowired private com.devmate.conversation.mapper.ConversationMapper conversationMapper;
    @MockitoBean private ProjectAccessService accessService;
    @MockitoBean private CurrentUserService currentUserService;
    @MockitoBean private ProjectService projectService;
    @MockitoBean private ContextRetrievalService retrievalService;
    @MockitoBean private ProjectUnderstandingReportService reportService;
    @MockitoBean private ProjectConversationModelRegistry modelRegistry;
    @MockitoBean(name = "conversationTaskExecutor") private TaskExecutor taskExecutor;

    private Project project;
    private KnowledgeChunk evidence;
    private ProjectConversationModel model;

    @BeforeEach
    void setUp() {
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(taskExecutor).execute(any(Runnable.class));

        AppUser user = new AppUser();
        user.setId(USER_ID);
        user.setUsername("conversation-user");
        user.setPasswordHash("not-used");
        user.setStatus("ACTIVE");
        user.setDeleted(0);
        userMapper.insert(user);
        when(currentUserService.getRequiredUser())
                .thenReturn(new AuthenticatedUser(USER_ID, "conversation-user"));

        project = new Project();
        project.setName("order-system");
        project.setDescription("订单系统");
        project.setSourceType("GIT");
        project.setSourceLocation("https://github.com/example/order.git");
        project.setDefaultBranch("main");
        project.setCurrentRevision(REVISION);
        project.setCurrentStructureVersion("source-structure-v2");
        project.setStatus("READY");
        project.setDeleted(0);
        project.setCreatedAt(LocalDateTime.now());
        project.setUpdatedAt(LocalDateTime.now());
        projectMapper.insert(project);

        KnowledgeDocument document = new KnowledgeDocument();
        document.setProjectId(project.getId());
        document.setSourceKind("SOURCE_CODE");
        document.setFileName("OrderService.java");
        document.setFilePath("src/main/java/com/example/OrderService.java");
        document.setPathHash("conversation-path");
        document.setFileType("JAVA");
        document.setContentHash("conversation-document");
        document.setRevision(REVISION);
        document.setStructureVersion("source-structure-v2");
        document.setStatus("READY");
        document.setChunkCount(1);
        document.setDeleted(0);
        document.setCreatedAt(LocalDateTime.now());
        document.setUpdatedAt(LocalDateTime.now());
        documentMapper.insert(document);

        evidence = new KnowledgeChunk();
        evidence.setProjectId(project.getId());
        evidence.setDocumentId(document.getId());
        evidence.setChunkIndex(0);
        evidence.setChunkType("METHOD");
        evidence.setSymbolName("com.example.OrderService#createOrder");
        evidence.setLanguage("java");
        evidence.setContent("public void createOrder() { orderMapper.insert(order); }");
        evidence.setContentHash("conversation-chunk");
        evidence.setTokenCount(12);
        evidence.setStartLine(20);
        evidence.setEndLine(22);
        evidence.setRevision(REVISION);
        evidence.setMetadataJson("{}");
        evidence.setCreatedAt(LocalDateTime.now());
        chunkMapper.insert(evidence);

        ProjectResponse projectResponse = ProjectResponse.from(project);
        when(projectService.getProject(project.getId())).thenReturn(projectResponse);
        when(retrievalService.search(eq(project.getId()), any())).thenReturn(retrieval());
        model = mock(ProjectConversationModel.class);
        when(model.providerName()).thenReturn("TEST");
        when(model.modelName()).thenReturn("test-model");
        when(modelRegistry.current()).thenReturn(model);
        when(modelRegistry.current("TEST", "test-model")).thenReturn(model);
    }

    @Autowired private ConversationStateService conversationState;

    @Test
    void cancellationDuringResearchPreservesToolSummaryAndNeverStartsAnswerStream() {
        var created=service.create(project.getId(),new CreateConversationRequest("取证取消"));
        when(model.supportsResearch()).thenReturn(true);
        var call=new com.devmate.agent.model.ReviewAgentToolCall("read-cancel","function",
                new com.devmate.agent.model.ReviewAgentToolCall.FunctionCall("read_code",
                        "{\"chunkId\":\""+evidence.getId()+"\"}"));
        when(model.research(any(),any())).thenReturn(reactor.core.publisher.Mono.just(
                new com.devmate.agent.model.ReviewAgentTurn(new com.devmate.agent.model.ReviewAgentMessage(
                        "assistant","",null,List.of(call)),0,0,0,"tool_calls")),
                reactor.core.publisher.Mono.defer(() -> {
                    var running=service.messages(project.getId(),created.id()).getLast();
                    service.cancel(project.getId(),created.id(),running.id());
                    return reactor.core.publisher.Mono.never();
                }));
        service.stream(project.getId(),created.id(),new CreateConversationMessageRequest("读取订单实现",java.util.UUID.randomUUID().toString()));
        var cancelled=service.messages(project.getId(),created.id()).getLast();
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(cancelled.context().actions().getFirst().status()).isEqualTo("SUCCEEDED");
        org.mockito.Mockito.verify(model,org.mockito.Mockito.never()).stream(any());
    }

    @Test
    void researchExpandsLongMethodAndPersistsCoverageForHistory() {
        evidence.setContent("public void createOrder() {\n" + "// padding\n".repeat(350) + "rollbackPayment();\n}");
        evidence.setEndLine(372); chunkMapper.updateById(evidence);
        when(model.supportsResearch()).thenReturn(true);
        var call = new com.devmate.agent.model.ReviewAgentToolCall("read-1", "function",
                new com.devmate.agent.model.ReviewAgentToolCall.FunctionCall("read_code",
                        "{\"chunkId\":\"" + evidence.getId() + "\",\"startLine\":371,\"endLine\":372}"));
        when(model.research(any(), any())).thenReturn(
                reactor.core.publisher.Mono.just(new com.devmate.agent.model.ReviewAgentTurn(
                        new com.devmate.agent.model.ReviewAgentMessage("assistant", "", null, List.of(call)),0,0,0,"tool_calls")),
                reactor.core.publisher.Mono.just(new com.devmate.agent.model.ReviewAgentTurn(
                        new com.devmate.agent.model.ReviewAgentMessage("assistant", "done", null, List.of()),0,0,0,"stop")));
        when(model.stream(any())).thenReturn(Flux.just("失败会调用 rollbackPayment [证据2]"));
        var created=service.create(project.getId(),new CreateConversationRequest("取证测试"));
        service.stream(project.getId(),created.id(),new CreateConversationMessageRequest("说明订单回滚",java.util.UUID.randomUUID().toString()));
        var prompt=ArgumentCaptor.forClass(ProjectConversationPrompt.class); verify(model).stream(prompt.capture());
        assertThat(prompt.getValue().messages().getLast().content()).contains("rollbackPayment();");
        var messages=service.messages(project.getId(),created.id());
        assertThat(messages.getLast().status()).isEqualTo("COMPLETED");
        assertThat(messages.getLast().context().actions().getFirst().tool()).isEqualTo("read_code");
        assertThat(messages.getLast().evidence()).anySatisfy(item -> {
            assertThat(item.startLine()).isEqualTo(371);assertThat(item.excerpt()).contains("rollbackPayment");
        });
    }

    @Test
    void retainsEarlierUserTopicsAndSkipsFailedQuestionsInFollowupMemory() {
        when(model.stream(any())).thenReturn(Flux.just("已解释 [证据1]"));
        var created=service.create(project.getId(),new CreateConversationRequest("多轮测试"));
        for(int i=0;i<8;i++) service.stream(project.getId(),created.id(),new CreateConversationMessageRequest(
                i==0 ? "订单创建的事务边界" : "继续",java.util.UUID.randomUUID().toString()));
        assertThat(conversationState.retrievalTopic(created.id(),project.getId())).isEqualTo("订单创建的事务边界");
        assertThat(conversationState.topicMemory(created.id(),project.getId(),12)).contains("订单创建的事务边界");
        assertThat(service.messages(project.getId(),created.id()).getLast().context().memoryUsed()).isTrue();
        when(model.stream(any())).thenReturn(Flux.error(new AiReviewException("测试失败")));
        service.stream(project.getId(),created.id(),new CreateConversationMessageRequest("失败问题不能成为主题",java.util.UUID.randomUUID().toString()));
        assertThat(conversationState.retrievalTopic(created.id(),project.getId())).isEqualTo("订单创建的事务边界");
    }

    @Test
    void keepsSavedV2ConversationGuidanceWhenNewDefaultIsV4() {
        when(model.stream(any())).thenReturn(Flux.just("旧会话回答"));
        var created = service.create(project.getId(), new CreateConversationRequest("旧会话"));
        var saved = conversationMapper.selectById(created.id());
        saved.setPromptVersion("project-conversation-v2"); conversationMapper.updateById(saved);
        service.stream(project.getId(), created.id(), new CreateConversationMessageRequest(
                "说明这个功能", "923e4567-e89b-42d3-a456-426614174000"));
        var captured = ArgumentCaptor.forClass(ProjectConversationPrompt.class); verify(model).stream(captured.capture());
        assertThat(captured.getValue().systemPrompt()).contains("先用两三句话").doesNotContain("第一层");
        assertThat(service.list(project.getId()).getFirst().promptVersion()).isEqualTo("project-conversation-v2");
    }

    @Test
    void persistsTwoStreamingTurnsAndPassesHistoryToTheSecondPrompt() {
        when(model.stream(any())).thenReturn(
                Flux.just("订单", "由服务层创建。"),
                Flux.just("它会", "写入订单表。")
        );
        var conversation = service.create(project.getId(), new CreateConversationRequest(null));

        service.stream(project.getId(), conversation.id(), new CreateConversationMessageRequest(
                "订单怎么创建？", "123e4567-e89b-42d3-a456-426614174000"
        ));
        service.stream(project.getId(), conversation.id(), new CreateConversationMessageRequest(
                "它会修改哪些数据？", "223e4567-e89b-42d3-a456-426614174000"
        ));

        List<com.devmate.conversation.dto.ConversationMessageResponse> messages =
                service.messages(project.getId(), conversation.id());
        assertThat(messages).extracting(item -> item.role() + ":" + item.status())
                .containsExactly("USER:COMPLETED", "ASSISTANT:COMPLETED",
                        "USER:COMPLETED", "ASSISTANT:COMPLETED");
        assertThat(messages.get(1).content()).isEqualTo("订单由服务层创建。");
        assertThat(messages.get(1).evidence()).singleElement()
                .satisfies(item -> assertThat(item.filePath()).endsWith("OrderService.java"));

        ArgumentCaptor<ProjectConversationPrompt> promptCaptor =
                ArgumentCaptor.forClass(ProjectConversationPrompt.class);
        verify(model, times(2)).stream(promptCaptor.capture());
        ProjectConversationPrompt second = promptCaptor.getAllValues().get(1);
        assertThat(second.messages()).extracting(ProjectConversationPrompt.Message::content)
                .anyMatch(content -> content.equals("订单由服务层创建。"))
                .anyMatch(content -> content.contains("它会修改哪些数据？") && content.contains("[证据1]"));
    }

    @Test
    void anchorsFollowupsToSavedBusinessEvidenceAndKeepsHistoryAfterListingAgain() {
        when(model.stream(any())).thenReturn(Flux.just("订单由服务创建。"));
        when(reportService.conversationSeeds(eq(project.getId()), eq(REVISION), any()))
                .thenReturn(List.of(evidence.getId()));
        var conversation = service.create(project.getId(), new CreateConversationRequest(null));
        service.stream(project.getId(), conversation.id(), new CreateConversationMessageRequest(
                "订单怎么创建？", "623e4567-e89b-42d3-a456-426614174000"));
        service.stream(project.getId(), conversation.id(), new CreateConversationMessageRequest(
                "它会改变哪些数据？", "723e4567-e89b-42d3-a456-426614174000"));
        var commands = ArgumentCaptor.forClass(RetrievalSearchCommand.class);
        verify(retrievalService, times(2)).search(eq(project.getId()), commands.capture());
        assertThat(commands.getAllValues().get(1)).satisfies(command -> {
            assertThat(command.query()).contains("订单怎么创建？", "它会改变哪些数据？");
            assertThat(command.seedChunkIds()).containsExactly(evidence.getId());
            assertThat(command.retrievalMode()).isEqualTo(RetrievalMode.LEXICAL);
        });
        assertThat(service.get(project.getId(), conversation.id()).id()).isEqualTo(conversation.id());
        assertThat(service.list(project.getId())).singleElement().satisfies(item -> {
            assertThat(item.title()).isEqualTo("订单怎么创建？");
            assertThat(item.promptVersion()).isEqualTo("project-conversation-v4");
        });
        assertThat(service.messages(project.getId(), conversation.id())).hasSize(4);
        var prompts = ArgumentCaptor.forClass(ProjectConversationPrompt.class);
        verify(model, times(2)).stream(prompts.capture());
        assertThat(prompts.getValue().systemPrompt()).contains("第一层", "不能声称运行验证", "不可信数据");
    }

    @Test
    void explicitCodeFollowupDoesNotInheritThePreviousTopic() {
        when(model.stream(any())).thenReturn(Flux.just("依据实现解释。"));
        var conversation = service.create(project.getId(), new CreateConversationRequest(null));
        service.stream(project.getId(), conversation.id(), new CreateConversationMessageRequest(
                "介绍订单业务", "823e4567-e89b-42d3-a456-426614174000"));
        service.stream(project.getId(), conversation.id(), new CreateConversationMessageRequest(
                "请继续解释 Signer.sign 和 Signer.unsign", "923e4567-e89b-42d3-a456-426614174000"));
        var commands = ArgumentCaptor.forClass(RetrievalSearchCommand.class);
        verify(retrievalService, times(2)).search(eq(project.getId()), commands.capture());
        assertThat(commands.getValue().query()).isEqualTo("请继续解释 Signer.sign 和 Signer.unsign");
        assertThat(commands.getValue().seedChunkIds()).isEmpty();
        assertThat(commands.getValue().retrievalMode()).isEqualTo(RetrievalMode.LEXICAL);
    }

    @Test
    void replaysCompletedAttemptWithoutAnotherModelCall() {
        when(model.stream(any())).thenReturn(Flux.just("第一次回答"));
        var conversation = service.create(project.getId(), new CreateConversationRequest("订单问题"));
        CreateConversationMessageRequest request = new CreateConversationMessageRequest(
                "订单怎么创建？", "323e4567-e89b-42d3-a456-426614174000"
        );

        service.stream(project.getId(), conversation.id(), request);
        service.stream(project.getId(), conversation.id(), request);

        verify(model, times(1)).stream(any());
        assertThat(messageMapper.selectList(null)).hasSize(2);
    }

    @Test
    void persistsReadableFailureAndReleasesConversationRunningKey() {
        when(model.stream(any())).thenReturn(Flux.error(new AiReviewException("模型额度不足")));
        var conversation = service.create(project.getId(), new CreateConversationRequest(null));

        service.stream(project.getId(), conversation.id(), new CreateConversationMessageRequest(
                "分析订单", "423e4567-e89b-42d3-a456-426614174000"
        ));

        ConversationMessage assistant = messageMapper.selectList(null).stream()
                .filter(item -> "ASSISTANT".equals(item.getMessageRole())).findFirst().orElseThrow();
        assertThat(assistant.getStatus()).isEqualTo("FAILED");
        assertThat(assistant.getErrorMessage()).isEqualTo("模型额度不足");
        assertThat(assistant.getRunningKey()).isNull();
    }

    @Test
    void sendsFailedEventWhenTaskExecutorRejectsExecution() {
        doThrow(new RuntimeException("Queue capacity exceeded"))
                .when(taskExecutor).execute(any(Runnable.class));
        var conversation = service.create(project.getId(), new CreateConversationRequest(null));

        service.stream(project.getId(), conversation.id(), new CreateConversationMessageRequest(
                "分析订单", "523e4567-e89b-42d3-a456-426614174000"
        ));

        ConversationMessage assistant = messageMapper.selectList(null).stream()
                .filter(item -> "ASSISTANT".equals(item.getMessageRole())).findFirst().orElseThrow();
        assertThat(assistant.getStatus()).isEqualTo("FAILED");
        assertThat(assistant.getErrorCode()).isEqualTo("EXECUTOR_REJECTED");
        assertThat(assistant.getErrorMessage()).contains("任务繁忙");
        assertThat(assistant.getRunningKey()).isNull();
    }

    @Test
    void rejectsProjectMembersBeforeResolvingModelWhenAccessFails() {
        doThrow(new BusinessException(com.devmate.common.error.ErrorCode.FORBIDDEN))
                .when(accessService).requireMember(project.getId());

        assertThatThrownBy(() -> service.create(project.getId(), new CreateConversationRequest(null)))
                .isInstanceOf(BusinessException.class);
        verify(modelRegistry, times(0)).current();
    }

    @Test
    void renamesAndSoftDeletesWithoutRemovingMessages() {
        when(model.stream(any())).thenReturn(Flux.just("已完成的回答"));
        var conversation = service.create(project.getId(), new CreateConversationRequest(null));
        service.stream(project.getId(), conversation.id(), new CreateConversationMessageRequest(
                "创建流程", "623e4567-e89b-42d3-a456-426614174000"));
        assertThat(service.rename(project.getId(), conversation.id(), "  我的业务说明  ").title()).isEqualTo("我的业务说明");
        assertThatThrownBy(() -> service.rename(project.getId(), conversation.id(), "   ")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.rename(project.getId(), conversation.id(), "x".repeat(101))).isInstanceOf(BusinessException.class);
        service.delete(project.getId(), conversation.id());
        assertThat(service.list(project.getId())).isEmpty();
        assertThat(messageMapper.selectCount(null)).isEqualTo(2);
        assertThatThrownBy(() -> service.messages(project.getId(), conversation.id())).hasMessageContaining("不存在");
        assertThatThrownBy(() -> service.rename(project.getId(), conversation.id(), "恢复")).hasMessageContaining("不存在");
    }

    @Test
    void cancelsQueuedTurnWithoutCallingModelAndAllowsAnotherQuestion() {
        java.util.concurrent.atomic.AtomicReference<Runnable> queued = new java.util.concurrent.atomic.AtomicReference<>();
        doAnswer(invocation -> { queued.set(invocation.getArgument(0)); return null; }).when(taskExecutor).execute(any(Runnable.class));
        var conversation = service.create(project.getId(), new CreateConversationRequest(null));
        var request = new CreateConversationMessageRequest("准备问题", "723e4567-e89b-42d3-a456-426614174000");
        service.stream(project.getId(), conversation.id(), request);
        var running = service.messages(project.getId(), conversation.id()).get(1);
        assertThat(service.get(project.getId(), conversation.id()).id()).isEqualTo(conversation.id());
        assertThatThrownBy(() -> service.rename(project.getId(), conversation.id(), "不能改")).hasMessageContaining("先停止");
        assertThatThrownBy(() -> service.delete(project.getId(), conversation.id())).hasMessageContaining("先停止");
        assertThat(service.cancel(project.getId(), conversation.id(), running.id()).cancellationRequested()).isTrue();
        assertThat(service.cancel(project.getId(), conversation.id(), running.id()).cancellationRequested()).isTrue();
        queued.get().run();
        var cancelled = service.messages(project.getId(), conversation.id()).get(1);
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(cancelled.content()).isEmpty();
        assertThat(messageMapper.selectById(running.id()).getRunningKey()).isNull();
        assertThat(service.cancel(project.getId(), conversation.id(), running.id()).cancellationRequested()).isFalse();
        service.stream(project.getId(), conversation.id(), request);
        verify(model, times(0)).stream(any());
        assertThat(service.messages(project.getId(), conversation.id())).hasSize(2);
        when(model.stream(any())).thenReturn(Flux.just("新一轮正常回答"));
        service.stream(project.getId(), conversation.id(), new CreateConversationMessageRequest(
                "另一个问题", "823e4567-e89b-42d3-a456-426614174000"));
        queued.get().run();
        assertThat(service.messages(project.getId(), conversation.id()).get(3).status()).isEqualTo("COMPLETED");
    }

    @Test
    void stoppingCancelsUpstreamAndPersistsPartialTextAndEvidence() {
        var conversation = service.create(project.getId(), new CreateConversationRequest(null));
        var upstreamCancelled = new java.util.concurrent.atomic.AtomicBoolean();
        when(model.stream(any())).thenReturn(Flux.concat(Flux.just("已生成的部分"), Flux.defer(() -> {
            var running = service.messages(project.getId(), conversation.id()).get(1);
            service.cancel(project.getId(), conversation.id(), running.id());
            return Flux.<String>never().doOnCancel(() -> upstreamCancelled.set(true));
        })));
        service.stream(project.getId(), conversation.id(), new CreateConversationMessageRequest(
                "需要停止的长问题", "923e4567-e89b-42d3-a456-426614174000"));
        var answer = service.messages(project.getId(), conversation.id()).get(1);
        assertThat(answer.status()).isEqualTo("CANCELLED");
        assertThat(answer.content()).isEqualTo("已生成的部分");
        assertThat(answer.evidence()).hasSize(1);
        assertThat(upstreamCancelled).isTrue();
    }

    @Test
    void rejectsForeignUsersForEveryManagementOperationAndWrongMessageIds() {
        var conversation = service.create(project.getId(), new CreateConversationRequest(null));
        assertThatThrownBy(() -> service.cancel(project.getId(), conversation.id(), -1L)).hasMessageContaining("消息不存在");
        when(currentUserService.getRequiredUser()).thenReturn(new AuthenticatedUser(99999L, "someone-else"));
        assertThatThrownBy(() -> service.get(project.getId(), conversation.id())).hasMessageContaining("无权");
        assertThatThrownBy(() -> service.rename(project.getId(), conversation.id(), "不能改")).hasMessageContaining("无权");
        assertThatThrownBy(() -> service.delete(project.getId(), conversation.id())).hasMessageContaining("无权");
        assertThatThrownBy(() -> service.cancel(project.getId(), conversation.id(), -1L)).hasMessageContaining("无权");
        assertThat(service.list(project.getId())).isEmpty();
    }

    @Test
    void createsWithAnExplicitSavedModelAndRejectsUnpairedSelection() {
        var selected = service.create(project.getId(), new CreateConversationRequest("明确选择", "TEST", "test-model"));
        assertThat(selected.modelName()).isEqualTo("test-model");
        verify(modelRegistry).current("TEST", "test-model");
        assertThatThrownBy(() -> service.create(project.getId(), new CreateConversationRequest(null, "TEST", null)))
                .hasMessageContaining("必须同时指定");
        assertThatThrownBy(() -> service.create(project.getId(), new CreateConversationRequest(null, null, "test-model")))
                .hasMessageContaining("必须同时指定");
    }

    @Test
    void restoresFocusedEvidenceAndStillReadsLegacyIdArrays() {
        evidence.setContent("header\n".repeat(200) + "def sign(value): return value"); evidence.setStartLine(1); evidence.setEndLine(201);
        chunkMapper.updateById(evidence);
        when(retrievalService.search(any(), any())).thenReturn(retrieval("def sign(value): return value", 201, 202));
        when(model.stream(any())).thenReturn(Flux.just("见证据中的实际方法。"));
        var conversation = service.create(project.getId(), new CreateConversationRequest(null));
        service.stream(project.getId(), conversation.id(), new CreateConversationMessageRequest("Signer.sign", "a23e4567-e89b-42d3-a456-426614174000"));
        var answer = service.messages(project.getId(), conversation.id()).get(1);
        assertThat(answer.evidence().getFirst().startLine()).isEqualTo(201);
        assertThat(answer.evidence().getFirst().endLine()).isEqualTo(201);
        assertThat(answer.evidence().getFirst().excerpt()).startsWith("def sign");
        var saved = messageMapper.selectById(answer.id()); saved.setEvidenceJson("[\"" + evidence.getId() + "\"]"); messageMapper.updateById(saved);
        assertThat(service.messages(project.getId(), conversation.id()).get(1).evidence().getFirst().excerpt()).startsWith("header");
    }

    private RetrievalSearchResponse retrieval() { return retrieval(evidence.getContent(), 20, 22); }
    private RetrievalSearchResponse retrieval(String excerpt, int start, int end) {
        RetrievalHitResponse hit = new RetrievalHitResponse(
                evidence.getId(), evidence.getDocumentId(),
                "src/main/java/com/example/OrderService.java", "SOURCE_CODE", "METHOD",
                evidence.getSymbolName(), start, end, 0.9, 12,
                List.of("VECTOR"), excerpt
        );
        return new RetrievalSearchResponse(project.getId(), REVISION, "订单", "hybrid-test",
                "HYBRID", "HYBRID", "LOCAL", "hash", true, 1, false, null,
                1, false, false, 6, 5000, 12, 1, 0, 0, List.of(hit), List.of());
    }
}
