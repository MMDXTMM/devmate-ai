package com.devmate.knowledge.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devmate.knowledge.entity.IndexTask;
import com.devmate.knowledge.entity.KnowledgeDocument;
import com.devmate.knowledge.entity.KnowledgeChunk;
import com.devmate.knowledge.entity.CodeReference;
import com.devmate.knowledge.mapper.IndexTaskMapper;
import com.devmate.knowledge.mapper.KnowledgeChunkMapper;
import com.devmate.knowledge.mapper.KnowledgeDocumentMapper;
import com.devmate.knowledge.mapper.CodeReferenceMapper;
import com.devmate.knowledge.mapper.EmbeddingVectorMapper;
import com.devmate.knowledge.source.GitCloneResult;
import com.devmate.knowledge.source.GitSourceClient;
import com.devmate.knowledge.source.SourceImportException;
import com.devmate.project.dto.CreateProjectRequest;
import com.devmate.project.dto.ProjectResponse;
import com.devmate.project.entity.Project;
import com.devmate.project.mapper.ProjectMapper;
import com.devmate.project.service.ProjectService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "devmate.source.workspace-root=${java.io.tmpdir}/devmate-import-tests")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SourceImportControllerTest {

    private static final String REVISION = "0123456789abcdef0123456789abcdef01234567";
    private static final String NEXT_REVISION = "89abcdef0123456789abcdef0123456789abcdef";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ProjectService projectService;
    @Autowired
    private ProjectMapper projectMapper;
    @Autowired
    private IndexTaskMapper indexTaskMapper;
    @Autowired
    private KnowledgeDocumentMapper documentMapper;
    @Autowired
    private KnowledgeChunkMapper chunkMapper;
    @Autowired
    private CodeReferenceMapper referenceMapper;
    @Autowired
    private EmbeddingVectorMapper vectorMapper;

    @MockitoBean
    private GitSourceClient gitSourceClient;

    @TempDir
    Path tempDir;

    @Test
    void importsGitRepositoryAndPersistsJavaMetadata() throws Exception {
        ProjectResponse project = createGitProject("import-success");
        mockSuccessfulClone();

        mockMvc.perform(post("/api/projects/{projectId}/imports", project.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.revision").value(REVISION))
                .andExpect(jsonPath("$.data.totalFiles").value(2))
                .andExpect(jsonPath("$.data.processedFiles").value(2))
                .andExpect(jsonPath("$.data.reusedFiles").value(0))
                .andExpect(jsonPath("$.data.cloneDurationMs", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.data.scanDurationMs", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.data.planDurationMs", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.data.parseDurationMs", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.data.persistDurationMs", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.data.totalDurationMs", greaterThanOrEqualTo(0)));

        Project storedProject = projectMapper.selectById(project.id());
        assertThat(storedProject.getStatus()).isEqualTo("READY");
        assertThat(storedProject.getCurrentRevision()).isEqualTo(REVISION);
        assertThat(storedProject.getLastIndexedAt()).isNotNull();

        assertThat(documentMapper.selectCount(Wrappers.lambdaQuery(KnowledgeDocument.class)
                .eq(KnowledgeDocument::getProjectId, project.id()))).isEqualTo(2);
        assertThat(chunkMapper.selectCount(Wrappers.lambdaQuery(KnowledgeChunk.class)
                .eq(KnowledgeChunk::getProjectId, project.id()))).isEqualTo(7);
        assertThat(chunkMapper.selectCount(Wrappers.lambdaQuery(KnowledgeChunk.class)
                .eq(KnowledgeChunk::getProjectId, project.id())
                .eq(KnowledgeChunk::getChunkType, "FILE_HEADER"))).isEqualTo(2);
        CodeReference reference = referenceMapper.selectOne(Wrappers.lambdaQuery(CodeReference.class)
                .eq(CodeReference::getProjectId, project.id())
                .eq(CodeReference::getReferenceKind, "METHOD_CALL")
                .last("LIMIT 1"));
        assertThat(reference.getReferenceName()).isEqualTo("helper");
        assertThat(reference.getTargetChunkId()).isNotNull();
        assertThat(indexTaskMapper.selectCount(Wrappers.lambdaQuery(IndexTask.class)
                .eq(IndexTask::getProjectId, project.id())
                .eq(IndexTask::getStatus, "SUCCEEDED"))).isEqualTo(1);
    }

    @Test
    void repeatedImportOfSameRevisionIsIdempotentForDocuments() throws Exception {
        ProjectResponse project = createGitProject("import-twice");
        mockSuccessfulClone();

        mockMvc.perform(post("/api/projects/{projectId}/imports", project.id()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/projects/{projectId}/embeddings/index", project.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.processedChunks").value(7));
        Set<Long> originalChunkIds = chunkMapper.selectList(Wrappers.lambdaQuery(KnowledgeChunk.class)
                        .eq(KnowledgeChunk::getProjectId, project.id()))
                .stream()
                .map(KnowledgeChunk::getId)
                .collect(java.util.stream.Collectors.toSet());
        mockMvc.perform(post("/api/projects/{projectId}/imports", project.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskType").value("INCREMENTAL"))
                .andExpect(jsonPath("$.data.processedFiles").value(0))
                .andExpect(jsonPath("$.data.reusedFiles").value(2))
                .andExpect(jsonPath("$.data.scanDurationMs").value(0))
                .andExpect(jsonPath("$.data.planDurationMs").value(0))
                .andExpect(jsonPath("$.data.parseDurationMs").value(0))
                .andExpect(jsonPath("$.data.totalDurationMs", greaterThanOrEqualTo(0)));

        assertThat(documentMapper.selectCount(Wrappers.lambdaQuery(KnowledgeDocument.class)
                .eq(KnowledgeDocument::getProjectId, project.id()))).isEqualTo(2);
        assertThat(chunkMapper.selectCount(Wrappers.lambdaQuery(KnowledgeChunk.class)
                .eq(KnowledgeChunk::getProjectId, project.id()))).isEqualTo(7);
        assertThat(chunkMapper.selectList(Wrappers.lambdaQuery(KnowledgeChunk.class)
                        .eq(KnowledgeChunk::getProjectId, project.id())))
                .extracting(KnowledgeChunk::getId)
                .containsExactlyInAnyOrderElementsOf(originalChunkIds);
        assertThat(chunkMapper.selectList(Wrappers.lambdaQuery(KnowledgeChunk.class)
                        .eq(KnowledgeChunk::getProjectId, project.id())))
                .allSatisfy(chunk -> assertThat(chunk.getVectorId()).isNotBlank());
        assertThat(vectorMapper.selectCount(null)).isEqualTo(7);
        assertThat(referenceMapper.selectCount(Wrappers.lambdaQuery(CodeReference.class)
                .eq(CodeReference::getProjectId, project.id()))).isEqualTo(1);
        assertThat(indexTaskMapper.selectCount(Wrappers.lambdaQuery(IndexTask.class)
                .eq(IndexTask::getProjectId, project.id()))).isEqualTo(2);
    }

    @Test
    void incrementallyImportsChangedFilesAndRebuildsReusedReferencesForNewRevision() throws Exception {
        ProjectResponse project = createGitProject("incremental-import");
        AtomicInteger cloneCount = new AtomicInteger();
        given(gitSourceClient.cloneRepository(anyString(), eq("main"), any(Path.class)))
                .willAnswer(invocation -> {
                    Path target = invocation.getArgument(2);
                    Path sources = target.resolve("src/main/java/com/example");
                    Files.createDirectories(sources);
                    Files.writeString(sources.resolve("Stable.java"), """
                            package com.example;
                            class Stable {
                                void run() { helper(); }
                                void helper() {}
                            }
                            """);
                    if (cloneCount.getAndIncrement() == 0) {
                        Files.writeString(sources.resolve("Changed.java"), """
                                package com.example;
                                class Changed { String value() { return "before"; } }
                                """);
                        Files.writeString(sources.resolve("Deleted.java"), """
                                package com.example;
                                class Deleted {}
                                """);
                        return new GitCloneResult(target, REVISION);
                    }
                    Files.writeString(sources.resolve("Changed.java"), """
                            package com.example;
                            class Changed { String value() { return "after"; } }
                            """);
                    Files.writeString(sources.resolve("Added.java"), """
                            package com.example;
                            class Added {}
                            """);
                    return new GitCloneResult(target, NEXT_REVISION);
                });

        mockMvc.perform(post("/api/projects/{projectId}/imports", project.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskType").value("FULL"))
                .andExpect(jsonPath("$.data.processedFiles").value(3))
                .andExpect(jsonPath("$.data.reusedFiles").value(0));
        Long previousReferenceTarget = referenceMapper.selectOne(
                Wrappers.lambdaQuery(CodeReference.class)
                        .eq(CodeReference::getProjectId, project.id())
                        .eq(CodeReference::getRevision, REVISION)
                        .last("LIMIT 1")
        ).getTargetChunkId();

        mockMvc.perform(post("/api/projects/{projectId}/imports", project.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskType").value("INCREMENTAL"))
                .andExpect(jsonPath("$.data.totalFiles").value(3))
                .andExpect(jsonPath("$.data.processedFiles").value(2))
                .andExpect(jsonPath("$.data.reusedFiles").value(1))
                .andExpect(jsonPath("$.data.planDurationMs", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.data.parseDurationMs", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.data.totalDurationMs", greaterThanOrEqualTo(0)));

        assertThat(documentMapper.selectList(Wrappers.lambdaQuery(KnowledgeDocument.class)
                        .eq(KnowledgeDocument::getProjectId, project.id())
                        .eq(KnowledgeDocument::getRevision, NEXT_REVISION)))
                .extracting(KnowledgeDocument::getFileName)
                .containsExactlyInAnyOrder("Stable.java", "Changed.java", "Added.java")
                .doesNotContain("Deleted.java");
        CodeReference rebuilt = referenceMapper.selectOne(Wrappers.lambdaQuery(CodeReference.class)
                .eq(CodeReference::getProjectId, project.id())
                .eq(CodeReference::getRevision, NEXT_REVISION)
                .last("LIMIT 1"));
        assertThat(rebuilt.getTargetChunkId()).isNotNull().isNotEqualTo(previousReferenceTarget);
        assertThat(chunkMapper.selectById(rebuilt.getSourceChunkId()).getRevision()).isEqualTo(NEXT_REVISION);
        assertThat(chunkMapper.selectById(rebuilt.getTargetChunkId()).getRevision()).isEqualTo(NEXT_REVISION);
        assertThat(documentMapper.selectCount(Wrappers.lambdaQuery(KnowledgeDocument.class)
                .eq(KnowledgeDocument::getProjectId, project.id()))).isEqualTo(6);
    }

    @Test
    void recordsFailureWhenCloneFails() throws Exception {
        ProjectResponse project = createGitProject("import-failure");
        given(gitSourceClient.cloneRepository(anyString(), anyString(), any(Path.class)))
                .willThrow(new SourceImportException("Git仓库克隆失败，请检查地址、分支和访问权限"));

        mockMvc.perform(post("/api/projects/{projectId}/imports", project.id()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(50000))
                .andExpect(jsonPath("$.message").value("Git仓库克隆失败，请检查地址、分支和访问权限"));

        assertThat(projectMapper.selectById(project.id()).getStatus()).isEqualTo("FAILED");
        IndexTask task = indexTaskMapper.selectOne(Wrappers.lambdaQuery(IndexTask.class)
                .eq(IndexTask::getProjectId, project.id())
                .last("LIMIT 1"));
        assertThat(task.getStatus()).isEqualTo("FAILED");
        assertThat(task.getFinishedAt()).isNotNull();
        assertImportMetricsAreNonNegative(task);
    }

    @Test
    void recordsFailureWhenJavaSyntaxCannotBeParsed() throws Exception {
        ProjectResponse project = createGitProject("invalid-java");
        given(gitSourceClient.cloneRepository(anyString(), eq("main"), any(Path.class)))
                .willAnswer(invocation -> {
                    Path target = invocation.getArgument(2);
                    Files.createDirectories(target);
                    Files.writeString(target.resolve("Broken.java"), "class Broken { void run( { }");
                    return new GitCloneResult(target, REVISION);
                });

        mockMvc.perform(post("/api/projects/{projectId}/imports", project.id()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Java语法解析失败")));

        assertThat(projectMapper.selectById(project.id()).getStatus()).isEqualTo("FAILED");
        IndexTask task = indexTaskMapper.selectOne(Wrappers.lambdaQuery(IndexTask.class)
                .eq(IndexTask::getProjectId, project.id())
                .last("LIMIT 1"));
        assertThat(task.getStatus()).isEqualTo("FAILED");
        assertThat(task.getErrorMessage()).contains("Broken.java");
        assertImportMetricsAreNonNegative(task);
        assertThat(documentMapper.selectCount(Wrappers.lambdaQuery(KnowledgeDocument.class)
                .eq(KnowledgeDocument::getProjectId, project.id()))).isZero();
        assertThat(chunkMapper.selectCount(Wrappers.lambdaQuery(KnowledgeChunk.class)
                .eq(KnowledgeChunk::getProjectId, project.id()))).isZero();
    }

    private void assertImportMetricsAreNonNegative(IndexTask task) {
        assertThat(task.getCloneDurationMs()).isNotNegative();
        assertThat(task.getScanDurationMs()).isNotNegative();
        assertThat(task.getPlanDurationMs()).isNotNegative();
        assertThat(task.getParseDurationMs()).isNotNegative();
        assertThat(task.getPersistDurationMs()).isNotNegative();
        assertThat(task.getTotalDurationMs()).isNotNegative();
    }

    @Test
    void rejectsNonGitProjectBeforeCreatingTask() throws Exception {
        ProjectResponse project = projectService.createProject(new CreateProjectRequest(
                "local-project", null, "LOCAL", tempDir.toString(), "main"
        ));

        mockMvc.perform(post("/api/projects/{projectId}/imports", project.id()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("第一版源码导入仅支持Git项目"));

        verifyNoInteractions(gitSourceClient);
        assertThat(indexTaskMapper.selectCount(Wrappers.lambdaQuery(IndexTask.class)
                .eq(IndexTask::getProjectId, project.id()))).isZero();
    }

    @Test
    void returnsLatestImportTask() throws Exception {
        ProjectResponse project = createGitProject("latest-task");
        mockSuccessfulClone();
        mockMvc.perform(post("/api/projects/{projectId}/imports", project.id()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/projects/{projectId}/imports/latest", project.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.totalFiles").value(2));
    }

    @Test
    void listsImportedFilesAndParsedSymbols() throws Exception {
        ProjectResponse project = createGitProject("source-structure");
        mockSuccessfulClone();
        mockMvc.perform(post("/api/projects/{projectId}/imports", project.id()))
                .andExpect(status().isOk());

        KnowledgeDocument appDocument = documentMapper.selectOne(
                Wrappers.lambdaQuery(KnowledgeDocument.class)
                        .eq(KnowledgeDocument::getProjectId, project.id())
                        .eq(KnowledgeDocument::getFileName, "App.java")
                        .last("LIMIT 1")
        );

        mockMvc.perform(get("/api/projects/{projectId}/sources", project.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").isString())
                .andExpect(jsonPath("$.data[0].packageName").value("com.example"))
                .andExpect(jsonPath("$.data[0].status").value("PARSED"));

        mockMvc.perform(get(
                        "/api/projects/{projectId}/sources/{documentId}/symbols",
                        project.id(),
                        appDocument.getId()
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].chunkType").value("FILE_HEADER"))
                .andExpect(jsonPath("$.data[0].symbolName").value("package com.example"))
                .andExpect(jsonPath("$.data[1].chunkType").value("CLASS"))
                .andExpect(jsonPath("$.data[1].symbolName").value("com.example.App"))
                .andExpect(jsonPath("$.data[1].annotations[0]").value("Deprecated"))
                .andExpect(jsonPath("$.data[2].chunkType").value("METHOD"))
                .andExpect(jsonPath("$.data[2].symbolName").value("com.example.App#run()"))
                .andExpect(jsonPath("$.data[2].startLine").isNumber());

        mockMvc.perform(get("/api/projects/{projectId}/sources/references", project.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].referenceKind").value("METHOD_CALL"))
                .andExpect(jsonPath("$.data[0].referenceName").value("helper"))
                .andExpect(jsonPath("$.data[0].sourceSymbolName").value("com.example.App#run()"))
                .andExpect(jsonPath("$.data[0].targetSymbolName").value("com.example.App#helper()"))
                .andExpect(jsonPath("$.data[0].resolved").value(true));
    }

    @Test
    void importsConfigurationDefinitionsAndLinksJavaReferencesWithoutPersistingSecrets() throws Exception {
        ProjectResponse project = createGitProject("configuration-context");
        given(gitSourceClient.cloneRepository(anyString(), eq("main"), any(Path.class)))
                .willAnswer(invocation -> {
                    Path target = invocation.getArgument(2);
                    Files.createDirectories(target.resolve("src/main/java/com/example"));
                    Files.createDirectories(target.resolve("src/main/resources"));
                    Files.createDirectories(target.resolve("src/main/resources/db/migration"));
                    Files.writeString(target.resolve("src/main/java/com/example/ReviewProperties.java"), """
                            package com.example;
                            import org.springframework.beans.factory.annotation.Value;
                            import org.springframework.boot.context.properties.ConfigurationProperties;
                            @TableName("review_task")
                            @ConfigurationProperties(prefix = "review")
                            class ReviewProperties {
                                @Value("${review.limit:20}")
                                private int limit;
                            }
                            """);
                    Files.writeString(target.resolve("src/main/resources/application.yml"), """
                            review:
                              limit: 50
                              mode: strict
                            datasource:
                              password: should-never-be-stored
                            """);
                    Files.writeString(target.resolve("src/main/resources/db/migration/V1__review.sql"), """
                            CREATE TABLE review_task (
                                id BIGINT NOT NULL,
                                review_limit INT NOT NULL,
                                PRIMARY KEY (id)
                            );
                            CREATE INDEX idx_review_task_limit ON review_task (review_limit);
                            """);
                    return new GitCloneResult(target, REVISION);
                });

        mockMvc.perform(post("/api/projects/{projectId}/imports", project.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalFiles").value(3));

        KnowledgeDocument configuration = documentMapper.selectOne(
                Wrappers.lambdaQuery(KnowledgeDocument.class)
                        .eq(KnowledgeDocument::getProjectId, project.id())
                        .eq(KnowledgeDocument::getFileName, "application.yml")
                        .last("LIMIT 1")
        );
        assertThat(configuration.getSourceKind()).isEqualTo("CONFIGURATION");
        assertThat(configuration.getFileType()).isEqualTo("YAML");

        KnowledgeChunk password = chunkMapper.selectOne(
                Wrappers.lambdaQuery(KnowledgeChunk.class)
                        .eq(KnowledgeChunk::getProjectId, project.id())
                        .eq(KnowledgeChunk::getSymbolName, "datasource.password")
                        .last("LIMIT 1")
        );
        assertThat(password.getChunkType()).isEqualTo("CONFIG_PROPERTY");
        assertThat(password.getContent()).isEqualTo("datasource.password = <redacted>");
        assertThat(password.getContent()).doesNotContain("should-never-be-stored");

        assertThat(referenceMapper.selectList(Wrappers.lambdaQuery(CodeReference.class)
                .eq(CodeReference::getProjectId, project.id())
                .in(CodeReference::getReferenceKind, "CONFIG_KEY", "CONFIG_PREFIX")))
                .hasSize(3)
                .allSatisfy(reference -> assertThat(reference.getTargetChunkId()).isNotNull());

        KnowledgeDocument databaseSchema = documentMapper.selectOne(
                Wrappers.lambdaQuery(KnowledgeDocument.class)
                        .eq(KnowledgeDocument::getProjectId, project.id())
                        .eq(KnowledgeDocument::getFileName, "V1__review.sql")
                        .last("LIMIT 1")
        );
        assertThat(databaseSchema.getSourceKind()).isEqualTo("DATABASE_SCHEMA");
        assertThat(databaseSchema.getFileType()).isEqualTo("SQL");
        assertThat(chunkMapper.selectList(Wrappers.lambdaQuery(KnowledgeChunk.class)
                .eq(KnowledgeChunk::getDocumentId, databaseSchema.getId())))
                .extracting(KnowledgeChunk::getSymbolName)
                .contains("review_task", "review_task.id", "review_task.review_limit",
                        "review_task#idx_review_task_limit");
        assertThat(referenceMapper.selectList(Wrappers.lambdaQuery(CodeReference.class)
                .eq(CodeReference::getProjectId, project.id())
                .eq(CodeReference::getReferenceKind, "DATABASE_TABLE")))
                .singleElement()
                .satisfies(reference -> assertThat(reference.getTargetChunkId()).isNotNull());

        mockMvc.perform(get(
                        "/api/projects/{projectId}/sources/{documentId}/symbols",
                        project.id(),
                        databaseSchema.getId()
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.chunkType == 'DATABASE_TABLE')].symbolName")
                        .value(hasItem("review_task")))
                .andExpect(jsonPath("$.data[?(@.chunkType == 'DATABASE_TABLE')].summary")
                        .value(hasItem("table review_task (columns: id, review_limit)")));

        mockMvc.perform(get("/api/projects/{projectId}/sources/references", project.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.referenceKind == 'CONFIG_KEY')].targetSymbolName")
                        .value(hasItem("review.limit")))
                .andExpect(jsonPath("$.data[?(@.referenceKind == 'CONFIG_KEY')].targetFilePath")
                        .value(hasItem("src/main/resources/application.yml")))
                .andExpect(jsonPath("$.data[?(@.referenceKind == 'DATABASE_TABLE')].targetSymbolName")
                        .value(hasItem("review_task")))
                .andExpect(jsonPath("$.data[?(@.referenceKind == 'DATABASE_TABLE')].targetFilePath")
                        .value(hasItem("src/main/resources/db/migration/V1__review.sql")));
    }

    @Test
    void rejectsSourceDocumentFromAnotherProject() throws Exception {
        ProjectResponse first = createGitProject("first-source-project");
        ProjectResponse second = createGitProject("second-source-project");
        mockSuccessfulClone();
        mockMvc.perform(post("/api/projects/{projectId}/imports", first.id()))
                .andExpect(status().isOk());
        KnowledgeDocument document = documentMapper.selectOne(
                Wrappers.lambdaQuery(KnowledgeDocument.class)
                        .eq(KnowledgeDocument::getProjectId, first.id())
                        .last("LIMIT 1")
        );

        mockMvc.perform(get(
                        "/api/projects/{projectId}/sources/{documentId}/symbols",
                        second.id(),
                        document.getId()
                ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("源码文件不存在"));
    }

    private ProjectResponse createGitProject(String name) {
        return projectService.createProject(new CreateProjectRequest(
                name,
                "源码导入测试",
                "GIT",
                "https://github.com/example/" + name + ".git",
                "main"
        ));
    }

    private void mockSuccessfulClone() {
        given(gitSourceClient.cloneRepository(anyString(), eq("main"), any(Path.class)))
                .willAnswer(invocation -> {
                    Path target = invocation.getArgument(2);
                    Files.createDirectories(target.resolve("src/main/java/com/example"));
                    Files.createDirectories(target.resolve("target/generated"));
                    Files.writeString(target.resolve("src/main/java/com/example/App.java"), """
                            package com.example;
                            @Deprecated
                            class App {
                                void run() { helper(); }
                                void helper() {}
                            }
                            """);
                    Files.writeString(target.resolve("src/main/java/com/example/UserService.java"), """
                            package com.example;
                            class UserService {
                                String find() { return "user"; }
                            }
                            """);
                    Files.writeString(target.resolve("target/generated/Ignored.java"), "class Ignored {}\n");
                    return new GitCloneResult(target, REVISION);
                });
    }
}
