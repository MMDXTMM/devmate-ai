package com.devmate.knowledge.service;

import com.devmate.agent.config.ProjectUnderstandingProperties;
import com.devmate.knowledge.entity.KnowledgeChunk;
import com.devmate.knowledge.entity.KnowledgeDocument;
import com.devmate.knowledge.mapper.KnowledgeChunkMapper;
import com.devmate.knowledge.mapper.KnowledgeDocumentMapper;
import org.junit.jupiter.api.Test;
import com.devmate.knowledge.source.ProjectReadme;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProjectGuideEvidenceServiceTest {
    @Test void choosesRootReadmeBeforeLocalizedAndNestedIntroductions() {
        assertThat(ProjectReadme.primary(List.of("docs/README.md", "README.zh-CN.md", "README.rst", "readme.md")))
                .contains("readme.md");
        assertThat(ProjectReadme.primary(List.of("docs/README.md", "README"))).contains("README");
        assertThat(ProjectReadme.primary(List.of("packages/a/README.md", "docs/README.rst"))).contains("docs/README.rst");
        assertThat(ProjectReadme.primary(List.of("main.py", "not-readme.md"))).isEmpty();
    }

    @Test void readsIntroductionBeforeManifestsAndSourceWithoutExhaustingCodeBudget() {
        var docs = mock(KnowledgeDocumentMapper.class); var chunks = mock(KnowledgeChunkMapper.class);
        var readme = document(1L, "README.md", "DOCUMENTATION");
        var source = document(2L, "main.py", "TEXT");
        var manifest = document(3L, "pyproject.toml", "MANIFEST");
        when(docs.selectList(any())).thenReturn(List.of(source, manifest, readme));
        when(chunks.selectList(any())).thenReturn(List.of(chunk(11L, "project introduction\n".repeat(100))),
                List.of(chunk(12L, "[project]\nname='example'")), List.of(chunk(13L, "print('example')")));
        var properties = new ProjectUnderstandingProperties(); properties.setMaxEvidenceCharacters(1000);
        var sample = new ProjectGuideEvidenceService(docs, chunks, properties).sample(1L, "revision");
        assertThat(sample.evidence()).extracting(item -> item.filePath()).containsExactly("README.md", "pyproject.toml", "main.py");
        assertThat(sample.evidence().getFirst().code()).hasSize(333);
        assertThat(sample.evidence().getFirst().truncated()).isTrue();
        assertThat(sample.coverage().readCharacters()).isLessThanOrEqualTo(1000);
        assertThat(sample.snapshot().readPolicy()).startsWith("readme-first-v2");
    }

    @Test void boundsLongReadmeAndKeepsMissingReadmeExplicit() {
        var docs = mock(KnowledgeDocumentMapper.class); var chunks = mock(KnowledgeChunkMapper.class);
        when(docs.selectList(any())).thenReturn(List.of(document(1L, "README", "DOCUMENTATION")));
        when(chunks.selectList(any())).thenReturn(List.of(chunk(11L, "one"), chunk(12L, "two"), chunk(13L, "three"), chunk(14L, "four")));
        var properties = new ProjectUnderstandingProperties(); properties.setMaxEvidenceChunks(2);
        var service = new ProjectGuideEvidenceService(docs, chunks, properties);
        assertThat(service.sample(1L, "revision").evidence()).hasSize(2);
        when(docs.selectList(any())).thenReturn(List.of(document(2L, "main.go", "TEXT")));
        when(chunks.selectList(any())).thenReturn(List.of(chunk(15L, "package main")));
        var sample = service.sample(1L, "revision");
        assertThat(sample.evidence()).hasSize(1);
        assertThat(sample.coverage().limitations().getFirst()).contains("未索引到 README");
    }

    private KnowledgeDocument document(Long id, String path, String type) {
        var doc = new KnowledgeDocument(); doc.setId(id); doc.setProjectId(1L); doc.setRevision("revision");
        doc.setFilePath(path); doc.setFileType(type); doc.setSourceKind(type.equals("DOCUMENTATION") ? "DOCUMENTATION" : "SOURCE_CODE");
        doc.setContentHash("hash"); return doc;
    }

    private KnowledgeChunk chunk(Long id, String content) {
        var chunk = new KnowledgeChunk(); chunk.setId(id); chunk.setChunkType("TEXT_EXCERPT");
        chunk.setContent(content); chunk.setStartLine(1); chunk.setEndLine(10); return chunk;
    }

    @Test void enforcesCharacterBudgetAndChangesDigestWhenImportedContentChanges() {
        var docs = mock(KnowledgeDocumentMapper.class); var chunks = mock(KnowledgeChunkMapper.class);
        var doc = new KnowledgeDocument(); doc.setId(10L); doc.setProjectId(1L); doc.setRevision("revision");
        doc.setFilePath("main.py"); doc.setFileType("TEXT"); doc.setSourceKind("SOURCE_CODE"); doc.setContentHash("one");
        var chunk = new KnowledgeChunk(); chunk.setId(42L); chunk.setDocumentId(10L); chunk.setProjectId(1L);
        chunk.setChunkType("TEXT_EXCERPT"); chunk.setContent("line\n".repeat(500)); chunk.setStartLine(1); chunk.setEndLine(500);
        when(docs.selectList(any())).thenReturn(List.of(doc)); when(chunks.selectList(any())).thenReturn(List.of(chunk));
        var properties = new ProjectUnderstandingProperties(); properties.setMaxEvidenceCharacters(1000); properties.setMaxEvidenceChunks(1);
        var service = new ProjectGuideEvidenceService(docs, chunks, properties);
        var first = service.sample(1L, "revision");
        assertThat(first.coverage().readCharacters()).isEqualTo(1000);
        assertThat(first.evidence()).singleElement().satisfies(item -> {
            assertThat(item.truncated()).isTrue(); assertThat(item.code()).hasSize(1000);
            assertThat(item.startLine()).isEqualTo(1); assertThat(item.endLine()).isEqualTo(500);
        });
        assertThat(service.sample(1L, "revision").snapshot()).isEqualTo(first.snapshot());
        doc.setContentHash("two");
        assertThat(service.sample(1L, "revision").snapshot().contentDigest()).isNotEqualTo(first.snapshot().contentDigest());
    }
}
