package com.devmate.knowledge.vector;

import com.devmate.knowledge.embedding.EmbeddingBatch;
import com.devmate.knowledge.embedding.EmbeddingException;
import com.devmate.knowledge.embedding.EmbeddingProvider;
import com.devmate.knowledge.embedding.EmbeddingProviderRegistry;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VectorRetrievalService {

    private final EmbeddingProviderRegistry providerRegistry;
    private final MySqlVectorStore vectorStore;

    public VectorRetrievalService(
            EmbeddingProviderRegistry providerRegistry,
            MySqlVectorStore vectorStore
    ) {
        this.providerRegistry = providerRegistry;
        this.vectorStore = vectorStore;
    }

    public VectorSearchResult search(Long projectId, String revision, String query, int limit) {
        EmbeddingProvider provider = providerRegistry.current();
        long indexed = vectorStore.count(
                projectId,
                revision,
                provider.providerName(),
                provider.modelName(),
                provider.dimensions()
        );
        if (indexed == 0) {
            return VectorSearchResult.unavailable("当前项目版本尚未使用所选模型建立向量索引");
        }
        try {
            EmbeddingBatch batch = provider.embed(List.of(query));
            return vectorStore.search(
                    projectId,
                    revision,
                    provider.providerName(),
                    provider.modelName(),
                    provider.dimensions(),
                    batch.vectors().getFirst(),
                    limit
            );
        } catch (EmbeddingException exception) {
            return VectorSearchResult.unavailable(exception.getMessage());
        }
    }

    public EmbeddingProvider currentProvider() {
        return providerRegistry.current();
    }
}
