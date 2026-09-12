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
    @MockitoBean private ProjectAccessService accessService;
    @MockitoBean private CurrentUserService currentUserService;
    @MockitoBean private ProjectService projectService;
    @MockitoBean private ContextRetrievalService retrievalService;
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

    private RetrievalSearchResponse retrieval() {
        RetrievalHitResponse hit = new RetrievalHitResponse(
                evidence.getId(), evidence.getDocumentId(),
                "src/main/java/com/example/OrderService.java", "SOURCE_CODE", "METHOD",
                evidence.getSymbolName(), 20, 22, 0.9, 12,
                List.of("VECTOR"), evidence.getContent()
        );
        return new RetrievalSearchResponse(project.getId(), REVISION, "订单", "hybrid-test",
                "HYBRID", "HYBRID", "LOCAL", "hash", true, 1, false, null,
                1, false, false, 6, 5000, 12, 1, 0, 0, List.of(hit), List.of());
    }
}
