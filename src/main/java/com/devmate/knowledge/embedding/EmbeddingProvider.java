package com.devmate.knowledge.embedding;

import java.util.List;

public interface EmbeddingProvider {

    String providerName();

    String modelName();

    int dimensions();

    EmbeddingBatch embed(List<String> texts);
}
