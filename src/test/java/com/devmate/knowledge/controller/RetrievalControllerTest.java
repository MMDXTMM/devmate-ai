package com.devmate.knowledge.controller;

import com.devmate.knowledge.entity.CodeReference;
import com.devmate.knowledge.entity.IndexTask;
import com.devmate.knowledge.entity.KnowledgeChunk;
import com.devmate.knowledge.entity.KnowledgeDocument;
import com.devmate.knowledge.mapper.CodeReferenceMapper;
import com.devmate.knowledge.mapper.IndexTaskMapper;
import com.devmate.knowledge.mapper.KnowledgeChunkMapper;
import com.devmate.knowledge.mapper.KnowledgeDocumentMapper;
import com.devmate.knowledge.mapper.RetrievalEvaluationCaseMapper;
import com.devmate.knowledge.mapper.RetrievalEvaluationRunMapper;
import com.devmate.project.entity.Project;
import com.devmate.project.mapper.ProjectMapper;
import com.devmate.review.dto.MappedSymbolResponse;
import com.devmate.review.entity.CodeReviewFile;
import com.devmate.review.entity.CodeReviewTask;
import com.devmate.review.mapper.CodeReviewFileMapper;
import com.devmate.review.mapper.CodeReviewTaskMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RetrievalControllerTest {

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
    @Autowired
    private IndexTaskMapper indexTaskMapper;
    @Autowired
    private CodeReviewTaskMapper reviewTaskMapper;
    @Autowired
    private CodeReviewFileMapper reviewFileMapper;
    @Autowired
    private RetrievalEvaluationCaseMapper evaluationCaseMapper;
    @Autowired
    private RetrievalEvaluationRunMapper evaluationRunMapper;
    @Autowired
    private ObjectMapper objectMapper;

    private Project project;
    private KnowledgeChunk reserveStock;
    private KnowledgeChunk saveOrder;

    @BeforeEach
    void setUp() {
        project = insertProject("retrieval-project", REVISION);
        KnowledgeDocument serviceDocument = insertDocument(
                project.getId(), "src/main/java/com/example/OrderService.java", REVISION
        );
        KnowledgeDocument repositoryDocument = insertDocument(
                project.getId(), "src/main/java/com/example/OrderRepository.java", REVISION
        );
        reserveStock = insertChunk(
                project.getId(), serviceDocument.getId(), "METHOD",
                "com.example.OrderService#reserveStock(Long)",
                "public void reserveStock(Long productId) { validateStock(productId); saveOrder(productId); }",
                "hash-reserve", REVISION, 10
        );
        saveOrder = insertChunk(
                project.getId(), repositoryDocument.getId(), "METHOD",
                "com.example.OrderRepository#saveOrder(Long)",
                "public void saveOrder(Long productId) { mapper.insert(productId); }",
                "hash-save", REVISION, 20
        );
        insertChunk(
                project.getId(), serviceDocument.getId(), "METHOD",
                "com.example.OrderService#unrelated()",
                "public void unrelated() { System.out.println(\"hello\"); }",
                "hash-unrelated", REVISION, 30
        );
        insertReference(project.getId(), reserveStock.getId(), saveOrder.getId(), REVISION);
    }

    @Test
    void ranksSeedAndGraphContextWithoutCrossProjectLeakage() throws Exception {
        Project other = insertProject("other-project", REVISION);
        KnowledgeDocument otherDocument = insertDocument(other.getId(), "Secret.java", REVISION);
        insertChunk(
                other.getId(), otherDocument.getId(), "METHOD", "Secret#reserveStock()",
                "private String secret = \"must-not-leak\";", "secret-hash", REVISION, 1
        );

        mockMvc.perform(post("/api/projects/{projectId}/retrieval/search", project.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "reserve stock order",
                                  "seedChunkIds": ["%s"],
                                  "topK": 3,
                                  "tokenBudget": 1000
                                }
                                """.formatted(reserveStock.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectId").value(project.getId().toString()))
                .andExpect(jsonPath("$.data.revision").value(REVISION))
                .andExpect(jsonPath("$.data.configVersion").value("lexical-graph-v1"))
                .andExpect(jsonPath("$.data.hits[0].chunkId").value(reserveStock.getId().toString()))
                .andExpect(jsonPath("$.data.hits[0].reasons", hasItem("DIFF_SYMBOL")))
                .andExpect(jsonPath("$.data.hits[1].chunkId").value(saveOrder.getId().toString()))
                .andExpect(jsonPath("$.data.hits[1].reasons", hasItem("OUTGOING_METHOD_CALL")))
                .andExpect(jsonPath("$.data.hits[*].excerpt", org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("must-not-leak"))
                )));
    }

    @Test
    void rejectsSeedFromAnotherProject() throws Exception {
        Project other = insertProject("seed-other", REVISION);
        KnowledgeDocument document = insertDocument(other.getId(), "Other.java", REVISION);
        KnowledgeChunk foreign = insertChunk(
                other.getId(), document.getId(), "METHOD", "Other#run()", "void run() {}",
                "foreign-hash", REVISION, 1
        );

        mockMvc.perform(post("/api/projects/{projectId}/retrieval/search", project.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"run method","seedChunkIds":["%s"]}
                                """.formatted(foreign.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("种子Chunk不属于当前项目版本"));
    }

    @Test
    void reportsTokenBudgetAndTopKTrimming() throws Exception {
        mockMvc.perform(post("/api/projects/{projectId}/retrieval/search", project.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"order service method","topK":1,"tokenBudget":100}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.selectedCount").value(1))
                .andExpect(jsonPath("$.data.usedTokens").value(org.hamcrest.Matchers.lessThanOrEqualTo(100)))
                .andExpect(jsonPath("$.data.trimmedCount").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.data.trimmed[*].reason", hasItem("TOP_K")));
    }

    @Test
    void validatesQueryAndUnknownRevision() throws Exception {
        mockMvc.perform(post("/api/projects/{projectId}/retrieval/search", project.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"   \"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/projects/{projectId}/retrieval/search", project.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"order","revision":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("该项目版本尚未建立知识索引"));
    }

    @Test
    void retrievesContextFromLatestSuccessfulDiff() throws Exception {
        IndexTask indexTask = insertIndexTask();
        CodeReviewTask reviewTask = insertReviewTask(indexTask.getId());
        insertReviewFile(reviewTask.getId());

        mockMvc.perform(post("/api/projects/{projectId}/review-diffs/latest/context", project.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.query", startsWith("com.example.OrderService#reserveStock")))
                .andExpect(jsonPath("$.data.hits", hasSize(org.hamcrest.Matchers.greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data.hits[0].chunkId").value(reserveStock.getId().toString()))
                .andExpect(jsonPath("$.data.hits[0].reasons", hasItem("DIFF_SYMBOL")));
    }

    @Test
    void persistsFixedEvaluationCaseAndCalculatesRetrievalMetrics() throws Exception {
        mockMvc.perform(post("/api/projects/{projectId}/retrieval/evaluation-cases", project.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "datasetVersion":"baseline-v1",
                                  "name":"reserve-stock-method",
                                  "query":"reserve stock order",
                                  "expectedFilePath":"src/main/java/com/example/OrderService.java",
                                  "expectedSymbolName":"com.example.OrderService#reserveStock(Long)",
                                  "topK":3
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.datasetVersion").value("baseline-v1"));

        mockMvc.perform(post("/api/projects/{projectId}/retrieval/evaluation-runs", project.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datasetVersion\":\"baseline-v1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.retrievalConfigVersion").value("lexical-graph-v1"))
                .andExpect(jsonPath("$.data.totalCases").value(1))
                .andExpect(jsonPath("$.data.resolvedCases").value(1))
                .andExpect(jsonPath("$.data.recallAtK").value(1.0))
                .andExpect(jsonPath("$.data.precisionAtK").value(0.333333))
                .andExpect(jsonPath("$.data.hitRateAtK").value(1.0))
                .andExpect(jsonPath("$.data.meanReciprocalRank").value(1.0))
                .andExpect(jsonPath("$.data.cases[0].relevantRetrieved").value(1));

        mockMvc.perform(get("/api/projects/{projectId}/retrieval/evaluation-runs/latest", project.getId())
                        .queryParam("datasetVersion", "baseline-v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cases[0].name").value("reserve-stock-method"));

        org.assertj.core.api.Assertions.assertThat(evaluationCaseMapper.selectCount(null)).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(evaluationRunMapper.selectCount(null)).isEqualTo(1);
    }

    @Test
    void rejectsDuplicateEvaluationCaseAndEscapingPath() throws Exception {
        String validCase = """
                {
                  "datasetVersion":"baseline-v1",
                  "name":"same-name",
                  "query":"reserve stock",
                  "expectedFilePath":"src/main/java/com/example/OrderService.java",
                  "topK":3
                }
                """;
        mockMvc.perform(post("/api/projects/{projectId}/retrieval/evaluation-cases", project.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCase))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/projects/{projectId}/retrieval/evaluation-cases", project.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCase))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40900));

        mockMvc.perform(post("/api/projects/{projectId}/retrieval/evaluation-cases", project.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "datasetVersion":"baseline-v1",
                                  "name":"escape",
                                  "query":"secret",
                                  "expectedFilePath":"../../secret.java",
                                  "topK":3
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("预期文件路径必须是项目内相对路径"));
    }

    private Project insertProject(String name, String revision) {
        LocalDateTime now = LocalDateTime.now();
        Project value = new Project();
        value.setName(name);
        value.setDescription("test");
        value.setSourceType("GIT");
        value.setSourceLocation("https://github.com/example/" + name + ".git");
        value.setDefaultBranch("main");
        value.setCurrentRevision(revision);
        value.setStatus("READY");
        value.setDeleted(0);
        value.setCreatedAt(now);
        value.setUpdatedAt(now);
        value.setLastIndexedAt(now);
        projectMapper.insert(value);
        return value;
    }

    private KnowledgeDocument insertDocument(Long projectId, String path, String revision) {
        LocalDateTime now = LocalDateTime.now();
        KnowledgeDocument value = new KnowledgeDocument();
        value.setProjectId(projectId);
        value.setSourceKind("SOURCE_CODE");
        value.setFileName(path.substring(path.lastIndexOf('/') + 1));
        value.setFilePath(path);
        value.setPathHash("path-" + projectId + "-" + path.hashCode());
        value.setFileType("JAVA");
        value.setContentHash("doc-" + projectId + "-" + path.hashCode());
        value.setRevision(revision);
        value.setStatus("PARSED");
        value.setChunkCount(3);
        value.setDeleted(0);
        value.setCreatedAt(now);
        value.setUpdatedAt(now);
        documentMapper.insert(value);
        return value;
    }

    private KnowledgeChunk insertChunk(
            Long projectId,
            Long documentId,
            String type,
            String symbol,
            String content,
            String hash,
            String revision,
            int line
    ) {
        KnowledgeChunk value = new KnowledgeChunk();
        value.setProjectId(projectId);
        value.setDocumentId(documentId);
        value.setChunkIndex(line);
        value.setChunkType(type);
        value.setSymbolName(symbol);
        value.setLanguage("JAVA");
        value.setContent(content);
        value.setContentHash(hash);
        value.setStartLine(line);
        value.setEndLine(line + 2);
        value.setRevision(revision);
        value.setCreatedAt(LocalDateTime.now());
        chunkMapper.insert(value);
        return value;
    }

    private void insertReference(Long projectId, Long sourceId, Long targetId, String revision) {
        CodeReference value = new CodeReference();
        value.setProjectId(projectId);
        value.setSourceChunkId(sourceId);
        value.setTargetChunkId(targetId);
        value.setRevision(revision);
        value.setReferenceKind("METHOD_CALL");
        value.setReferenceName("saveOrder");
        value.setArgumentCount(1);
        value.setStartLine(11);
        value.setEndLine(11);
        value.setCreatedAt(LocalDateTime.now());
        referenceMapper.insert(value);
    }

    private IndexTask insertIndexTask() {
        IndexTask value = new IndexTask();
        value.setProjectId(project.getId());
        value.setTaskType("FULL");
        value.setRevision(REVISION);
        value.setStatus("SUCCEEDED");
        value.setTotalFiles(2);
        value.setProcessedFiles(2);
        value.setFailedFiles(0);
        value.setCreatedAt(LocalDateTime.now());
        value.setStartedAt(LocalDateTime.now());
        value.setFinishedAt(LocalDateTime.now());
        indexTaskMapper.insert(value);
        return value;
    }

    private CodeReviewTask insertReviewTask(Long indexTaskId) {
        CodeReviewTask value = new CodeReviewTask();
        value.setProjectId(project.getId());
        value.setIndexTaskId(indexTaskId);
        value.setBaseRevision("cccccccccccccccccccccccccccccccccccccccc");
        value.setTargetRevision(REVISION);
        value.setTriggerType("MANUAL");
        value.setStatus("SUCCEEDED");
        value.setChangedFiles(1);
        value.setFullyMappedFiles(1);
        value.setPartiallyMappedFiles(0);
        value.setSkippedFiles(0);
        value.setCreatedAt(LocalDateTime.now());
        value.setStartedAt(LocalDateTime.now());
        value.setFinishedAt(LocalDateTime.now());
        reviewTaskMapper.insert(value);
        return value;
    }

    private void insertReviewFile(Long reviewTaskId) throws Exception {
        CodeReviewFile value = new CodeReviewFile();
        value.setReviewTaskId(reviewTaskId);
        value.setProjectId(project.getId());
        value.setOldPath("src/main/java/com/example/OrderService.java");
        value.setNewPath("src/main/java/com/example/OrderService.java");
        value.setChangeType("MODIFY");
        value.setCoverageStatus("FULL");
        value.setAdditions(1);
        value.setDeletions(0);
        value.setBaseChangedLinesJson("[]");
        value.setChangedLinesJson("[{\"startLine\":10,\"endLine\":10}]");
        value.setMappedSymbolsJson(objectMapper.writeValueAsString(List.of(
                new MappedSymbolResponse(
                        reserveStock.getId(), "TARGET", "METHOD",
                        reserveStock.getSymbolName(), 10, 12
                )
        )));
        value.setCreatedAt(LocalDateTime.now());
        reviewFileMapper.insert(value);
    }
}
