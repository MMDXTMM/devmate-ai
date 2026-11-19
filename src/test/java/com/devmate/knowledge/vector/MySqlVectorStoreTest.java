package com.devmate.knowledge.vector;

import com.devmate.knowledge.config.EmbeddingProperties;
import com.devmate.knowledge.entity.EmbeddingVector;
import com.devmate.knowledge.mapper.EmbeddingVectorMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MySqlVectorStoreTest {
    @Test
    void readsLaterPagesAndKeepsBestMatchesWithBoundedResultMemory() {
        var mapper = mock(EmbeddingVectorMapper.class);
        List<EmbeddingVector> first = IntStream.range(0, 256).mapToObj(i -> vector(i, "[0,1]")).toList();
        when(mapper.selectList(any())).thenReturn(first, List.of(vector(256, "[1,0]")));
        var store = new MySqlVectorStore(mapper, new EmbeddingProperties(), new ObjectMapper());
        var result = store.search(1L, "revision", "TEST", "model", 2, new float[]{1,0}, 1);
        assertThat(result.matches()).containsExactly(new VectorMatch(256L, 1.0));
        assertThat(result.indexedVectorCount()).isEqualTo(257);
        assertThat(result.limitReached()).isFalse();
        verify(mapper, times(2)).selectList(any());
    }
    @Test
    void reportsScanLimitAndDoesNotLabelPartialScanAsComplete() {
        var mapper = mock(EmbeddingVectorMapper.class);
        var properties = new EmbeddingProperties(); properties.setMaxVectorScan(1);
        when(mapper.selectList(any())).thenReturn(List.of(vector(1,"[1,0]"), vector(2,"[1,0]")));
        var result = new MySqlVectorStore(mapper, properties, new ObjectMapper())
                .search(1L,"revision","TEST","model",2,new float[]{1,0},1);
        assertThat(result.indexedVectorCount()).isEqualTo(1);
        assertThat(result.limitReached()).isTrue();
        assertThat(result.degradationReason()).contains("部分索引");
    }
    private EmbeddingVector vector(int id, String json) {
        var value = new EmbeddingVector(); value.setChunkId((long)id);
        value.setVectorId(String.format("%05d",id)); value.setVectorJson(json); return value;
    }
}
