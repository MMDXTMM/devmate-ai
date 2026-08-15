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
                .andExpect(jsonPath("$.data.analysisMode").value("STATIC_CODE_EVIDENCE_V1"))
                .andExpect(jsonPath("$.data.moduleCount").value(1))
                .andExpect(jsonPath("$.data.endpointCount").value(1))
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
