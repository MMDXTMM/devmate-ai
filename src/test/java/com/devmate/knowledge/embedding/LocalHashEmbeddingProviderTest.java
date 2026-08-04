package com.devmate.knowledge.embedding;

import com.devmate.knowledge.config.EmbeddingProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocalHashEmbeddingProviderTest {

    @Test
    void producesDeterministicNormalizedCodeAwareVectors() {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setLocalDimensions(128);
        LocalHashEmbeddingProvider provider = new LocalHashEmbeddingProvider(properties);

        float[] first = provider.embed(List.of("SearchService semanticSearch vector store"))
                .vectors().getFirst();
        float[] same = provider.embed(List.of("SearchService semanticSearch vector store"))
                .vectors().getFirst();
        float[] related = provider.embed(List.of("semantic vector search"))
                .vectors().getFirst();
        float[] unrelated = provider.embed(List.of("database migration checksum"))
                .vectors().getFirst();

        assertThat(first).containsExactly(same);
        assertThat(norm(first)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(cosine(first, related)).isGreaterThan(cosine(first, unrelated));
    }

    private double norm(float[] vector) {
        double value = 0.0;
        for (float item : vector) {
            value += item * item;
        }
        return Math.sqrt(value);
    }

    private double cosine(float[] left, float[] right) {
        double value = 0.0;
        for (int index = 0; index < left.length; index++) {
            value += left[index] * right[index];
        }
        return value;
    }
}
