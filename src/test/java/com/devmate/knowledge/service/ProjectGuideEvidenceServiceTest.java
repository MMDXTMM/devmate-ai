package com.devmate.knowledge.service;

import com.devmate.agent.config.ProjectUnderstandingProperties;
import com.devmate.knowledge.entity.KnowledgeChunk;
import com.devmate.knowledge.entity.KnowledgeDocument;
import com.devmate.knowledge.mapper.KnowledgeChunkMapper;
import com.devmate.knowledge.mapper.KnowledgeDocumentMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProjectGuideEvidenceServiceTest {
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
