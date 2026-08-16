package com.devmate.knowledge.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devmate.agent.model.ProjectUnderstandingModel;
import com.devmate.agent.model.ProjectUnderstandingModelRegistry;
import com.devmate.agent.model.ProjectUnderstandingModelResult;
import com.devmate.common.error.BusinessException;
import com.devmate.knowledge.dto.BusinessCodeEvidenceResponse;
import com.devmate.knowledge.dto.BusinessFeatureDetailResponse;
import com.devmate.knowledge.dto.BusinessFeatureResponse;
import com.devmate.knowledge.dto.BusinessModuleResponse;
import com.devmate.knowledge.dto.CreateProjectUnderstandingReportRequest;
import com.devmate.knowledge.dto.ProjectBusinessMapResponse;
import com.devmate.knowledge.dto.ProjectOnboardingResponse;
import com.devmate.knowledge.entity.KnowledgeChunk;
import com.devmate.knowledge.entity.KnowledgeDocument;
import com.devmate.knowledge.entity.ProjectUnderstandingReport;
import com.devmate.knowledge.mapper.KnowledgeChunkMapper;
import com.devmate.knowledge.mapper.KnowledgeDocumentMapper;
import com.devmate.knowledge.mapper.ProjectUnderstandingReportMapper;
import com.devmate.project.entity.Project;
import com.devmate.project.mapper.ProjectMapper;
import com.devmate.user.entity.AppUser;
import com.devmate.user.mapper.AppUserMapper;
import com.devmate.user.security.AuthenticatedUser;
import com.devmate.user.service.CurrentUserService;
import com.devmate.user.service.ProjectAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProjectUnderstandingReportServiceTest {
    private static final long USER_ID = 9201L;
    private static final String REVISION = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Autowired private ProjectUnderstandingReportService service;
    @Autowired private ProjectMapper projectMapper;
    @Autowired private AppUserMapper userMapper;
    @Autowired private KnowledgeDocumentMapper documentMapper;
    @Autowired private KnowledgeChunkMapper chunkMapper;
    @Autowired private ProjectUnderstandingReportMapper reportMapper;
    @MockitoBean private ProjectAccessService accessService;
    @MockitoBean private CurrentUserService currentUserService;
    @MockitoBean private ProjectBusinessMapService businessMapService;
    @MockitoBean private ProjectUnderstandingModelRegistry modelRegistry;

    private Project project;
    private KnowledgeChunk evidenceChunk;

    @BeforeEach
    void setUp() {
        AppUser user = new AppUser();
        user.setId(USER_ID);
        user.setUsername("understanding-user");
        user.setPasswordHash("not-used");
        user.setStatus("ACTIVE");
        user.setDeleted(0);
        userMapper.insert(user);
        when(currentUserService.getRequiredUser()).thenReturn(new AuthenticatedUser(USER_ID, "understanding-user"));

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
        document.setFileName("OrderController.java");
        document.setFilePath("src/main/java/com/example/OrderController.java");
        document.setPathHash("path-hash");
        document.setFileType("JAVA");
        document.setContentHash("content-hash");
        document.setRevision(REVISION);
        document.setStructureVersion("source-structure-v2");
        document.setStatus("READY");
        document.setChunkCount(1);
        document.setDeleted(0);
        document.setCreatedAt(LocalDateTime.now());
        document.setUpdatedAt(LocalDateTime.now());
        documentMapper.insert(document);

        evidenceChunk = new KnowledgeChunk();
        evidenceChunk.setProjectId(project.getId());
        evidenceChunk.setDocumentId(document.getId());
        evidenceChunk.setChunkIndex(0);
        evidenceChunk.setChunkType("METHOD");
        evidenceChunk.setSymbolName("com.example.OrderController#createOrder");
        evidenceChunk.setLanguage("java");
        evidenceChunk.setContent("public void createOrder() { orderService.create(); }");
        evidenceChunk.setContentHash("chunk-hash");
        evidenceChunk.setTokenCount(10);
        evidenceChunk.setStartLine(10);
        evidenceChunk.setEndLine(12);
        evidenceChunk.setRevision(REVISION);
        evidenceChunk.setMetadataJson("{}");
        evidenceChunk.setCreatedAt(LocalDateTime.now());
        chunkMapper.insert(evidenceChunk);

        BusinessFeatureResponse feature = new BusinessFeatureResponse(
                evidenceChunk.getId(), "创建订单", "接收创建订单请求", List.of("POST"), "/orders",
                "com.example.OrderController", document.getFilePath(), 10, 12, 2, true
        );
        ProjectBusinessMapResponse map = new ProjectBusinessMapResponse(
                REVISION, "STATIC_CODE_EVIDENCE_V2", "订单业务地图", 1, 1,
                new ProjectOnboardingResponse("订单系统", "Spring Web 分层架构", List.of("REST接口"),
                        List.of(), List.of(), List.of(), List.of(), List.of("运行时规则待确认")),
                List.of(new BusinessModuleResponse("order", "订单管理", "管理订单", "OrderController",
                        document.getFilePath(), 1, 20, List.of(feature))), List.of("静态分析存在边界")
        );
        when(businessMapService.getBusinessMap(project.getId())).thenReturn(map);
        when(businessMapService.getFeatureDetail(project.getId(), evidenceChunk.getId())).thenReturn(
                new BusinessFeatureDetailResponse(feature, "Controller → Service", List.of("写入订单"),
                        List.of(new BusinessCodeEvidenceResponse(
                                evidenceChunk.getId(), document.getId(), "CONTROLLER", evidenceChunk.getSymbolName(),
                                document.getFilePath(), 10, 12, "订单入口", evidenceChunk.getContent(), false,
                                evidenceChunk.getContent().length()
                        )))
        );
    }

    @Test
    void generatesEvidenceBackedReportAndReusesTheSamePaidAttempt() {
        ProjectUnderstandingModel model = successfulModel(String.valueOf(evidenceChunk.getId()));
        when(modelRegistry.current()).thenReturn(model);
        CreateProjectUnderstandingReportRequest request = new CreateProjectUnderstandingReportRequest(
                REVISION, "123e4567-e89b-42d3-a456-426614174000"
        );

        var first = service.create(project.getId(), request);
        var repeated = service.create(project.getId(), request);

        assertThat(first.status()).isEqualTo("SUCCEEDED");
        assertThat(first.businessFlows()).singleElement().satisfies(flow -> {
            assertThat(flow.name()).isEqualTo("创建订单");
            assertThat(flow.evidence()).singleElement().satisfies(evidence -> {
                assertThat(evidence.chunkId()).isEqualTo(String.valueOf(evidenceChunk.getId()));
                assertThat(evidence.filePath()).endsWith("OrderController.java");
                assertThat(evidence.code()).contains("orderService.create");
            });
        });
        assertThat(repeated.id()).isEqualTo(first.id());
        assertThat(first.totalTokens()).isEqualTo(30);
        verify(model, times(1)).analyze(any());
        ProjectUnderstandingReport stored = reportMapper.selectById(first.id());
        assertThat(stored.getRunningKey()).isNull();
        assertThat(stored.getReportJson()).doesNotContain("orderService.create");
        verify(accessService, times(2)).requireMember(project.getId());
    }

    @Test
    void rejectsHallucinatedEvidenceAndPersistsReadableFailure() {
        ProjectUnderstandingModel model = successfulModel("999999999999");
        when(modelRegistry.current()).thenReturn(model);

        assertThatThrownBy(() -> service.create(project.getId(), new CreateProjectUnderstandingReportRequest(
                REVISION, "223e4567-e89b-42d3-a456-426614174000"
        ))).isInstanceOf(BusinessException.class)
                .hasMessage("模型报告没有可验证的业务流程证据");

        ProjectUnderstandingReport stored = reportMapper.selectOne(
                Wrappers.lambdaQuery(ProjectUnderstandingReport.class)
                        .eq(ProjectUnderstandingReport::getProjectId, project.getId())
                        .last("LIMIT 1")
        );
        assertThat(stored.getStatus()).isEqualTo("FAILED");
        assertThat(stored.getErrorMessage()).isEqualTo("模型报告没有可验证的业务流程证据");
        assertThat(stored.getRunningKey()).isNull();
    }

    @Test
    void rejectsNonMembersBeforeResolvingThePaidModel() {
        doThrow(new BusinessException(com.devmate.common.error.ErrorCode.FORBIDDEN))
                .when(accessService).requireMember(project.getId());

        assertThatThrownBy(() -> service.create(project.getId(), new CreateProjectUnderstandingReportRequest(
                REVISION, "323e4567-e89b-42d3-a456-426614174000"
        ))).isInstanceOf(BusinessException.class);

        verify(modelRegistry, times(0)).current();
        assertThat(reportMapper.selectCount(Wrappers.lambdaQuery(ProjectUnderstandingReport.class)
                .eq(ProjectUnderstandingReport::getProjectId, project.getId()))).isZero();
    }

    @Test
    void recoversStaleRunningReportBeforeStartingANewAttempt() {
        ProjectUnderstandingReport stale = new ProjectUnderstandingReport();
        stale.setProjectId(project.getId());
        stale.setUserId(USER_ID);
        stale.setRevision(REVISION);
        stale.setProvider("TEST");
        stale.setModelName("old-model");
        stale.setPromptVersion("project-understanding-v1");
        stale.setStatus("RUNNING");
        stale.setPromptTokens(0);
        stale.setCompletionTokens(0);
        stale.setTotalTokens(0);
        stale.setAttemptKey("423e4567-e89b-42d3-a456-426614174000");
        stale.setRunningKey(project.getId() + ":" + REVISION);
        stale.setCreatedAt(LocalDateTime.now().minusMinutes(20));
        stale.setStartedAt(LocalDateTime.now().minusMinutes(20));
        reportMapper.insert(stale);
        ProjectUnderstandingModel model = successfulModel(String.valueOf(evidenceChunk.getId()));
        when(modelRegistry.current()).thenReturn(model);

        var created = service.create(project.getId(), new CreateProjectUnderstandingReportRequest(
                REVISION, "523e4567-e89b-42d3-a456-426614174000"
        ));

        ProjectUnderstandingReport recovered = reportMapper.selectById(stale.getId());
        assertThat(recovered.getStatus()).isEqualTo("FAILED");
        assertThat(recovered.getErrorCode()).isEqualTo("STALE_TASK");
        assertThat(recovered.getErrorMessage()).isEqualTo("报告生成任务超时，可重新发起");
        assertThat(recovered.getRunningKey()).isNull();
        assertThat(created.status()).isEqualTo("SUCCEEDED");
    }

    private ProjectUnderstandingModel successfulModel(String evidenceId) {
        ProjectUnderstandingModel model = mock(ProjectUnderstandingModel.class);
        when(model.providerName()).thenReturn("TEST");
        when(model.modelName()).thenReturn("test-model");
        when(model.analyze(any())).thenReturn(new ProjectUnderstandingModelResult(
                "这是一个订单系统", "Controller 接收请求并交给 Service 处理",
                List.of(new ProjectUnderstandingModelResult.BusinessFlow(
                        "创建订单", "用户提交订单", List.of("接收请求", "写入订单"),
                        List.of("POST /orders"), List.of("新增订单记录"), List.of(evidenceId)
                )),
                List.of(new ProjectUnderstandingModelResult.ReadingGuide(
                        1, "订单入口", "先理解外部请求", List.of(evidenceId)
                )), List.of("库存扣减规则待运行确认"), 10, 20, 30
        ));
        return model;
    }
}
