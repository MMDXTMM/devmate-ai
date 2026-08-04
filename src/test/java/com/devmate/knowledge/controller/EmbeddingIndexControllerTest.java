package com.devmate.knowledge.controller;

import com.devmate.knowledge.entity.KnowledgeChunk;
import com.devmate.knowledge.entity.KnowledgeDocument;
import com.devmate.knowledge.mapper.EmbeddingIndexTaskMapper;
import com.devmate.knowledge.mapper.EmbeddingVectorMapper;
import com.devmate.knowledge.mapper.KnowledgeChunkMapper;
import com.devmate.knowledge.mapper.KnowledgeDocumentMapper;
import com.devmate.project.entity.Project;
import com.devmate.project.mapper.ProjectMapper;
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

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EmbeddingIndexControllerTest {

    private static final String REVISION = "dddddddddddddddddddddddddddddddddddddddd";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ProjectMapper projectMapper;
    @Autowired
    private KnowledgeDocumentMapper documentMapper;
    @Autowired
    private KnowledgeChunkMapper chunkMapper;
    @Autowired
    private EmbeddingVectorMapper vectorMapper;
    @Autowired
    private EmbeddingIndexTaskMapper taskMapper;

    private Project project;
    private KnowledgeChunk searchChunk;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();
        project = new Project();
        project.setName("embedding-project");
        project.setDescription("test");
        project.setSourceType("GIT");
        project.setSourceLocation("https://github.com/example/embedding.git");
        project.setDefaultBranch("main");
        project.setCurrentRevision(REVISION);
        project.setStatus("READY");
        project.setDeleted(0);
        project.setCreatedAt(now);
        project.setUpdatedAt(now);
        project.setLastIndexedAt(now);
        projectMapper.insert(project);

        KnowledgeDocument document = new KnowledgeDocument();
        document.setProjectId(project.getId());
        document.setSourceKind("SOURCE_CODE");
        document.setFileName("SearchService.java");
        document.setFilePath("src/main/java/com/example/SearchService.java");
        document.setPathHash("embedding-path");
        document.setFileType("JAVA");
        document.setContentHash("embedding-document");
        document.setRevision(REVISION);
        document.setStatus("PARSED");
        document.setChunkCount(2);
        document.setDeleted(0);
        document.setCreatedAt(now);
        document.setUpdatedAt(now);
        documentMapper.insert(document);

        searchChunk = insertChunk(
                document.getId(), 1, "com.example.SearchService#semanticSearch(String)",
                "public List<Result> semanticSearch(String question) { return vectorStore.search(question); }",
                "embedding-chunk-search"
        );
        insertChunk(
                document.getId(), 2, "com.example.SearchService#health()",
                "public String health() { return \"UP\"; }",
                "embedding-chunk-health"
        );
    }

    @Test
    void buildsIdempotentIndexAndUsesHybridRetrieval() throws Exception {
        mockMvc.perform(post("/api/projects/{projectId}/embeddings/index", project.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.provider").value("LOCAL"))
                .andExpect(jsonPath("$.data.modelName").value("code-hash-v1"))
                .andExpect(jsonPath("$.data.totalChunks").value(2))
                .andExpect(jsonPath("$.data.processedChunks").value(2))
                .andExpect(jsonPath("$.data.skippedChunks").value(0));

        org.assertj.core.api.Assertions.assertThat(vectorMapper.selectCount(null)).isEqualTo(2);
        KnowledgeChunk indexed = chunkMapper.selectById(searchChunk.getId());
        org.assertj.core.api.Assertions.assertThat(indexed.getVectorId()).startsWith("vec_");
        org.assertj.core.api.Assertions.assertThat(documentMapper.selectById(indexed.getDocumentId()).getStatus())
                .isEqualTo("INDEXED");

        mockMvc.perform(post("/api/projects/{projectId}/retrieval/search", project.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query":"semantic vector search",
                                  "retrievalMode":"HYBRID",
                                  "topK":2,
                                  "tokenBudget":1000
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestedMode").value("HYBRID"))
                .andExpect(jsonPath("$.data.executedMode").value("HYBRID"))
                .andExpect(jsonPath("$.data.vectorIndexAvailable").value(true))
                .andExpect(jsonPath("$.data.embeddingProvider").value("LOCAL"))
                .andExpect(jsonPath("$.data.hits[0].chunkId").value(searchChunk.getId().toString()))
                .andExpect(jsonPath("$.data.hits[0].reasons", hasItem("VECTOR_SIMILARITY")));

        mockMvc.perform(post("/api/projects/{projectId}/embeddings/index", project.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.processedChunks").value(0))
                .andExpect(jsonPath("$.data.skippedChunks").value(2));

        mockMvc.perform(get("/api/projects/{projectId}/embeddings/tasks/latest", project.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"));

        mockMvc.perform(post("/api/projects/{projectId}/retrieval/evaluation-cases", project.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "datasetVersion":"hybrid-v1",
                                  "name":"semantic-search",
                                  "query":"semantic vector search",
                                  "expectedFilePath":"src/main/java/com/example/SearchService.java",
                                  "expectedSymbolName":"com.example.SearchService#semanticSearch(String)",
                                  "topK":2
                                }
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/projects/{projectId}/retrieval/evaluation-runs", project.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"datasetVersion":"hybrid-v1","retrievalMode":"HYBRID"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.retrievalMode").value("HYBRID"))
                .andExpect(jsonPath("$.data.retrievalConfigVersion")
                        .value("lexical-graph-v1+local:code-hash-v1:256"))
                .andExpect(jsonPath("$.data.hitRateAtK").value(1.0));

        org.assertj.core.api.Assertions.assertThat(taskMapper.selectCount(null)).isEqualTo(2);
    }

    private KnowledgeChunk insertChunk(
            Long documentId,
            int index,
            String symbol,
            String content,
            String contentHash
    ) {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setProjectId(project.getId());
        chunk.setDocumentId(documentId);
        chunk.setChunkIndex(index);
        chunk.setChunkType("METHOD");
        chunk.setSymbolName(symbol);
        chunk.setLanguage("JAVA");
        chunk.setContent(content);
        chunk.setContentHash(contentHash);
        chunk.setStartLine(index * 10);
        chunk.setEndLine(index * 10 + 2);
        chunk.setRevision(REVISION);
        chunk.setCreatedAt(LocalDateTime.now());
        chunkMapper.insert(chunk);
        return chunk;
    }
}
