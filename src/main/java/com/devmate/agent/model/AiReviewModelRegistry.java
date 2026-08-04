package com.devmate.agent.model;

import com.devmate.agent.config.AiReviewProperties;
import com.devmate.common.error.BusinessException;
import com.devmate.common.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class AiReviewModelRegistry {

    private final AiReviewProperties properties;
    private final Map<String, AiReviewModel> providers;

    public AiReviewModelRegistry(AiReviewProperties properties, List<AiReviewModel> providers) {
        this.properties = properties;
        this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(
                provider -> provider.providerName().toUpperCase(Locale.ROOT),
                Function.identity()
        ));
    }

    public AiReviewModel current() {
        String configured = properties.getProvider() == null
                ? "DASHSCOPE"
                : properties.getProvider().trim().toUpperCase(Locale.ROOT);
        AiReviewModel provider = providers.get(configured);
        if (provider == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "不支持的AI审查模型提供方");
        }
        return provider;
    }
}
