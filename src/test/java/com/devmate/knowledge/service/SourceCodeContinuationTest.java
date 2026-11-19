package com.devmate.knowledge.service;

import com.devmate.knowledge.dto.SourceSymbolDetailResponse;
import com.devmate.knowledge.entity.KnowledgeChunk;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class SourceCodeContinuationTest {
    @Test void continuesLongCodeWithoutLosingTextOrMixingLineNumbers() {
        var chunk = new KnowledgeChunk(); chunk.setId(1L); chunk.setDocumentId(2L); chunk.setRevision("abc");
        chunk.setStartLine(10); chunk.setContent("数据🌱\n".repeat(6000)); chunk.setEndLine(6010);
        StringBuilder reconstructed = new StringBuilder();
        int offset = 0;
        do {
            var part = SourceSymbolDetailResponse.from(chunk, List.of(), offset);
            assertThat(part.code().length()).isLessThanOrEqualTo(16000);
            assertThat(part.startLine()).isEqualTo(10 + (int)chunk.getContent().substring(0, offset).chars().filter(c -> c == '\n').count());
            assertThat(part.revision()).isEqualTo("abc");
            reconstructed.append(part.code());
            offset = part.nextOffset() == null ? -1 : part.nextOffset();
        } while (offset >= 0);
        assertThat(reconstructed.toString()).isEqualTo(chunk.getContent());
        assertThatThrownBy(() -> SourceSymbolDetailResponse.from(chunk, List.of(), -1)).hasMessageContaining("范围");
    }
}
