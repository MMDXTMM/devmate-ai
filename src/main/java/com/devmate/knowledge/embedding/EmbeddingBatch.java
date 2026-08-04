package com.devmate.knowledge.embedding;

import java.util.List;

public record EmbeddingBatch(List<float[]> vectors) {

    public EmbeddingBatch {
        vectors = List.copyOf(vectors);
    }
}
