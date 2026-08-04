package com.devmate.knowledge.embedding;

import com.devmate.common.error.BusinessException;
import com.devmate.common.error.ErrorCode;
import com.devmate.knowledge.config.EmbeddingProperties;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class EmbeddingProviderRegistry {

    private final EmbeddingProperties properties;
    private final Map<String, EmbeddingProvider> providers;

    public EmbeddingProviderRegistry(
            EmbeddingProperties properties,
            java.util.List<EmbeddingProvider> providers
    ) {
        this.properties = properties;
        this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(
                provider -> provider.providerName().toUpperCase(Locale.ROOT),
                Function.identity()
        ));
    }

    public EmbeddingProvider current() {
        String name = properties.getProvider() == null
                ? "LOCAL"
                : properties.getProvider().trim().toUpperCase(Locale.ROOT);
        EmbeddingProvider provider = providers.get(name);
        if (provider == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "不支持的Embedding提供方");
        }
        return provider;
    }
}
