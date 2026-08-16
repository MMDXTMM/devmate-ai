package com.devmate.knowledge.controller;

import com.devmate.knowledge.entity.CodeReference;
import com.devmate.knowledge.entity.KnowledgeChunk;
import com.devmate.knowledge.entity.KnowledgeDocument;
import com.devmate.knowledge.mapper.CodeReferenceMapper;
import com.devmate.knowledge.mapper.KnowledgeChunkMapper;
import com.devmate.knowledge.mapper.KnowledgeDocumentMapper;
import com.devmate.project.entity.Project;
import com.devmate.project.mapper.ProjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProjectBusinessMapControllerTest {

    private static final String REVISION = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ProjectMapper projectMapper;
    @Autowired
    private KnowledgeDocumentMapper documentMapper;
    @Autowired
    private KnowledgeChunkMapper chunkMapper;
    @Autowired
    private CodeReferenceMapper referenceMapper;

    private Project project;
    private KnowledgeChunk controllerMethod;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();
        project = new Project();
        project.setName("business-map-demo");
        project.setDescription("帮助团队管理代码审查任务");
        project.setSourceType("GIT");
        project.setSourceLocation("https://github.com/example/demo.git");
        project.setDefaultBranch("main");
        project.setCurrentRevision(REVISION);
        project.setCurrentStructureVersion("source-structure-v2");
        project.setStatus("READY");
        project.setDeleted(0);
        project.setCreatedAt(now);
        project.setUpdatedAt(now);
        projectMapper.insert(project);

        KnowledgeDocument controllerDocument = document(
                "ProjectController.java",
                "src/main/java/com/example/project/ProjectController.java",
                "controller-path"
        );
        KnowledgeChunk controllerClass = chunk(
                controllerDocument,
                0,
                "CLASS",
                "com.example.project.ProjectController",
                """
                        @RestController
                        @RequestMapping("/api/projects")
                        public class ProjectController { }
                        """,
                "{\"annotations\":[\"RestController\",\"RequestMapping\"]}",
                10,
                40
        );
        controllerMethod = chunk(
                controllerDocument,
                1,
                "METHOD",
                "com.example.project.ProjectController#create(CreateProjectRequest)",
                """
                        @PostMapping
                        public ProjectResponse create(CreateProjectRequest request) {
                            return projectService.create(request);
                        }
                        """,
                "{\"annotations\":[\"PostMapping\"],\"parameterCount\":1}",
                20,
                24
        );

        KnowledgeDocument serviceDocument = document(
                "ProjectService.java",
                "src/main/java/com/example/project/ProjectService.java",
                "service-path"
        );
        KnowledgeChunk serviceMethod = chunk(
                serviceDocument,
                0,
                "METHOD",
                "com.example.project.ProjectService#create(CreateProjectRequest)",
                "ProjectResponse create(CreateProjectRequest request);",
                "{\"annotations\":[],\"parameterCount\":1}",
                30,
                30
        );
        KnowledgeDocument implementationDocument = document(
                "ProjectServiceImpl.java",
                "src/main/java/com/example/project/service/ProjectServiceImpl.java",
                "service-impl-path"
        );
        KnowledgeChunk implementationMethod = chunk(
                implementationDocument,
                0,
                "METHOD",
                "com.example.project.service.ProjectServiceImpl#create(CreateProjectRequest)",
                """
                        public ProjectResponse create(CreateProjectRequest request) {
                            projectMapper.insert(project);
                            return ProjectResponse.from(project);
                        }
                        """,
                "{\"annotations\":[\"Override\"],\"parameterCount\":1}",
                35,
                39
        );
        reference(controllerMethod, null, "METHOD_CALL", "create", "projectService");
        reference(implementationMethod, null, "DATA_ACCESS", "insert", "projectMapper");

        KnowledgeDocument statusDocument = document(
                "ReviewStatus.java",
                "src/main/java/com/example/review/ReviewStatus.java",
                "status-path"
        );
        chunk(
                statusDocument,
                0,
                "CLASS",
                "com.example.review.ReviewStatus",
                "public enum ReviewStatus { CREATED, RUNNING, COMPLETED, FAILED; }",
                "{\"annotations\":[]}",
                1,
                1
        );
        KnowledgeDocument schemaDocument = document(
                "V1__review.sql",
                "src/main/resources/db/migration/V1__review.sql",
                "schema-path"
        );
        schemaDocument.setSourceKind("DATABASE_SCHEMA");
        schemaDocument.setFileType("SQL");
        documentMapper.updateById(schemaDocument);
        chunk(
                schemaDocument,
                0,
                "DATABASE_TABLE",
                "review_task",
                "CREATE TABLE review_task (...);",
                "{}",
                1,
                5
        );

        controllerDocument.setChunkCount(2);
        documentMapper.updateById(controllerDocument);
        serviceDocument.setChunkCount(1);
        documentMapper.updateById(serviceDocument);
        implementationDocument.setChunkCount(1);
        documentMapper.updateById(implementationDocument);
    }

    @Test
    void exposesBusinessModulesEndpointsAndEvidenceBackedImplementation() throws Exception {
        mockMvc.perform(get("/api/projects/{projectId}/business-map", project.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysisMode").value("STATIC_CODE_EVIDENCE_V2"))
                .andExpect(jsonPath("$.data.moduleCount").value(1))
                .andExpect(jsonPath("$.data.endpointCount").value(1))
                .andExpect(jsonPath("$.data.onboarding.purpose").value(
                        org.hamcrest.Matchers.containsString("帮助团队管理代码审查任务")
                ))
                .andExpect(jsonPath("$.data.onboarding.architectureSummary").value(
                        org.hamcrest.Matchers.containsString("Controller 模块接收")
                ))
                .andExpect(jsonPath("$.data.onboarding.coreJourneys[0].name").value("项目管理"))
                .andExpect(jsonPath("$.data.onboarding.coreJourneys[0].apiEntries[0]").value(
                        "POST /api/projects · 创建或执行项目管理"
                ))
                .andExpect(jsonPath("$.data.onboarding.coreJourneys[0].implementationFlow[2]").value(
                        org.hamcrest.Matchers.containsString("ProjectServiceImpl.create")
                ))
                .andExpect(jsonPath("$.data.onboarding.coreJourneys[0].dataOperations[0]").value(
                        "insert：projectMapper.insert()"
                ))
                .andExpect(jsonPath("$.data.onboarding.stateModels[0].name").value("ReviewStatus"))
                .andExpect(jsonPath("$.data.onboarding.stateModels[0].values[3]").value("FAILED"))
                .andExpect(jsonPath("$.data.onboarding.stateModels[0].chunkId").isString())
                .andExpect(jsonPath("$.data.onboarding.dataAssets[0].name").value("review_task"))
                .andExpect(jsonPath("$.data.onboarding.dataAssets[0].chunkId").isString())
                .andExpect(jsonPath("$.data.onboarding.readingOrder[0].category").value("业务入口"))
                .andExpect(jsonPath("$.data.modules[0].name").value("项目管理"))
                .andExpect(jsonPath("$.data.modules[0].features[0].id").isString())
                .andExpect(jsonPath("$.data.modules[0].features[0].httpMethods[0]").value("POST"))
                .andExpect(jsonPath("$.data.modules[0].features[0].path").value("/api/projects"))
                .andExpect(jsonPath("$.data.modules[0].features[0].implementationSteps").value(3))
                .andExpect(jsonPath("$.data.modules[0].features[0].accessesData").value(true));

        mockMvc.perform(get(
                        "/api/projects/{projectId}/business-map/features/{featureId}",
                        project.getId(),
                        controllerMethod.getId()
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.flowSummary").value(
                        "ProjectController.create(CreateProjectRequest) → ProjectService.create(CreateProjectRequest)"
                                + " → ProjectServiceImpl.create(CreateProjectRequest)"
                ))
                .andExpect(jsonPath("$.data.dataOperations[0]").value("insert：projectMapper.insert()"))
                .andExpect(jsonPath("$.data.implementation[0].layer").value("CONTROLLER"))
                .andExpect(jsonPath("$.data.implementation[0].code").value(org.hamcrest.Matchers.containsString("@PostMapping")))
                .andExpect(jsonPath("$.data.implementation[1].layer").value("SERVICE"))
                .andExpect(jsonPath("$.data.implementation[2].layer").value("SERVICE"))
                .andExpect(jsonPath("$.data.implementation[2].code").value(org.hamcrest.Matchers.containsString("projectMapper.insert")));
    }

    @Test
    void rejectsNonEndpointFeature() throws Exception {
        KnowledgeDocument document = document("Helper.java", "src/Helper.java", "helper-path");
        KnowledgeChunk helper = chunk(
                document, 0, "METHOD", "com.example.Helper#run()", "void run() {}",
                "{\"annotations\":[]}", 1, 1
        );

        mockMvc.perform(get(
                        "/api/projects/{projectId}/business-map/features/{featureId}",
                        project.getId(),
                        helper.getId()
                ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("所选方法不是可识别的Web接口入口"));
    }

    @Test
    void explainsCommonDianpingControllersAsUserFacingBusinessCapabilities() throws Exception {
        controllerEndpoint("Blog", "/blog", "saveBlog", "PostMapping", "blog-controller");
        controllerEndpoint("User", "/user", "sendCode", "PostMapping", "user-controller");
        controllerEndpoint("Shop", "/shop", "queryById", "GetMapping", "shop-controller");
        controllerEndpoint("Voucher", "/voucher", "queryVoucherOfShop", "GetMapping", "voucher-controller");

        mockMvc.perform(get("/api/projects/{projectId}/business-map", project.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboarding.purpose").value(
                        org.hamcrest.Matchers.containsString("本地生活探店主线")
                ))
                .andExpect(jsonPath("$.data.onboarding.purpose").value(
                        org.hamcrest.Matchers.containsString("发布、浏览和点赞探店笔记")
                ))
                .andExpect(jsonPath("$.data.modules[0].name").value("探店笔记与互动"))
                .andExpect(jsonPath("$.data.modules[0].description").value(
                        "用户可以发布探店笔记、浏览热门或关注内容、点赞并查看互动用户。"
                ))
                .andExpect(jsonPath("$.data.modules[0].features[0].name").value("发布探店笔记"))
                .andExpect(jsonPath("$.data.onboarding.coreJourneys[1].goal").value(
                        "用户可以发布探店笔记、浏览热门或关注内容、点赞并查看互动用户。"
                ));
    }

    private void controllerEndpoint(
            String controllerBase,
            String path,
            String methodName,
            String mappingAnnotation,
            String pathHash
    ) {
        KnowledgeDocument document = document(
                controllerBase + "Controller.java",
                "src/main/java/com/example/dianping/" + controllerBase + "Controller.java",
                pathHash
        );
        chunk(
                document,
                0,
                "CLASS",
                "com.example.dianping." + controllerBase + "Controller",
                "@RestController\n@RequestMapping(\"" + path + "\")\npublic class "
                        + controllerBase + "Controller { }",
                "{\"annotations\":[\"RestController\",\"RequestMapping\"]}",
                1,
                10
        );
        chunk(
                document,
                1,
                "METHOD",
                "com.example.dianping." + controllerBase + "Controller#" + methodName + "()",
                "@" + mappingAnnotation + "\npublic Object " + methodName + "() { return null; }",
                "{\"annotations\":[\"" + mappingAnnotation + "\"]}",
                5,
                7
        );
        document.setChunkCount(2);
        documentMapper.updateById(document);
    }

    private KnowledgeDocument document(String fileName, String filePath, String pathHash) {
        LocalDateTime now = LocalDateTime.now();
        KnowledgeDocument document = new KnowledgeDocument();
        document.setProjectId(project.getId());
        document.setSourceKind("SOURCE_CODE");
        document.setFileName(fileName);
        document.setFilePath(filePath);
        document.setPathHash(pathHash);
        document.setFileType("JAVA");
        document.setContentHash(pathHash + "-content");
        document.setRevision(REVISION);
        document.setStructureVersion("source-structure-v2");
        document.setStatus("PARSED");
        document.setChunkCount(0);
        document.setDeleted(0);
        document.setCreatedAt(now);
        document.setUpdatedAt(now);
        documentMapper.insert(document);
        return document;
    }

    private KnowledgeChunk chunk(
            KnowledgeDocument document,
            int index,
            String type,
            String symbol,
            String content,
            String metadata,
            int startLine,
            int endLine
    ) {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setProjectId(project.getId());
        chunk.setDocumentId(document.getId());
        chunk.setChunkIndex(index);
        chunk.setChunkType(type);
        chunk.setSymbolName(symbol);
        chunk.setLanguage("JAVA");
        chunk.setContent(content);
        chunk.setContentHash("hash-" + document.getId() + "-" + index);
        chunk.setStartLine(startLine);
        chunk.setEndLine(endLine);
        chunk.setRevision(REVISION);
        chunk.setMetadataJson(metadata);
        chunk.setCreatedAt(LocalDateTime.now());
        chunkMapper.insert(chunk);
        return chunk;
    }

    private void reference(
            KnowledgeChunk source,
            KnowledgeChunk target,
            String kind,
            String name,
            String qualifier
    ) {
        CodeReference reference = new CodeReference();
        reference.setProjectId(project.getId());
        reference.setSourceChunkId(source.getId());
        reference.setTargetChunkId(target == null ? null : target.getId());
        reference.setRevision(REVISION);
        reference.setReferenceKind(kind);
        reference.setReferenceName(name);
        reference.setQualifier(qualifier);
        reference.setArgumentCount(1);
        reference.setStartLine(source.getStartLine());
        reference.setEndLine(source.getStartLine());
        reference.setCreatedAt(LocalDateTime.now());
        referenceMapper.insert(reference);
    }
}
