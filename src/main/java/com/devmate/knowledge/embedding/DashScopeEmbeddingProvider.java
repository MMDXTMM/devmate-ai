package com.devmate.knowledge.embedding;

import com.devmate.agent.model.ModelConnectionSnapshot;
import com.devmate.agent.service.SpringAiChatClientFactory;
import com.devmate.knowledge.config.EmbeddingProperties;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;

@Component
public class DashScopeEmbeddingProvider implements EmbeddingProvider {

    private final EmbeddingProperties properties;
    private final SpringAiChatClientFactory modelFactory;

    public DashScopeEmbeddingProvider(
            EmbeddingProperties properties,
            SpringAiChatClientFactory modelFactory
    ) {
        this.properties = properties;
        this.modelFactory = modelFactory;
    }

    @Override
    public String providerName() {
        return "DASHSCOPE";
    }

    @Override
    public String modelName() {
        return properties.getModel();
    }

    @Override
    public int dimensions() {
        return properties.getDimensions();
    }

    @Override
    public EmbeddingBatch embed(List<String> texts) {
        validateConfiguration();
        if (texts == null || texts.isEmpty() || texts.size() > Math.min(properties.getBatchSize(), 10)) {
            throw new EmbeddingException("远端Embedding批次大小不合法");
        }
        try {
            ModelConnectionSnapshot connection = new ModelConnectionSnapshot(
                    "DASHSCOPE", properties.getModel(), trimTrailingSlash(properties.getBaseUrl()),
                    properties.getApiKey()
            );
            OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                    .model(properties.getModel())
                    .dimensions(properties.getDimensions())
                    .encodingFormat("float")
                    .build();
            EmbeddingResponse response = modelFactory.createEmbeddingModel(
                    connection, properties.getDimensions(),
                    Duration.ofSeconds(5), Duration.ofSeconds(30)
            ).call(new EmbeddingRequest(texts, options));
            if (response == null || response.getResults().size() != texts.size()) {
                throw new EmbeddingException("远端Embedding返回数量不一致");
            }
            List<float[]> vectors = response.getResults().stream()
                    .sorted(Comparator.comparingInt(item -> item.getIndex() == null ? 0 : item.getIndex()))
                    .map(item -> toVector(item.getOutput()))
                    .toList();
            return new EmbeddingBatch(vectors);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 429) {
                throw new EmbeddingException("远端Embedding请求过于频繁或额度不足，请稍后重试并检查额度", exception);
            }
            throw new EmbeddingException("远端Embedding调用失败", exception);
        } catch (RestClientException exception) {
            throw new EmbeddingException("远端Embedding调用失败", exception);
        } catch (NonTransientAiException exception) {
            if (exception.getMessage() != null && exception.getMessage().trim().startsWith("429")) {
                throw new EmbeddingException("远端Embedding请求过于频繁或额度不足，请稍后重试并检查额度", exception);
            }
            throw new EmbeddingException("远端Embedding调用失败", exception);
        }
    }

    private float[] toVector(float[] embedding) {
        if (embedding == null || embedding.length != dimensions()) {
            throw new EmbeddingException("远端Embedding维度不符合配置");
        }
        return embedding;
    }

    private void validateConfiguration() {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new EmbeddingException("未配置远端Embedding API Key");
        }
        if (!StringUtils.hasText(properties.getBaseUrl())
                || !properties.getBaseUrl().startsWith("https://")) {
            throw new EmbeddingException("远端Embedding地址必须是HTTPS");
        }
        if (properties.getDimensions() < 64 || properties.getDimensions() > 2048) {
            throw new EmbeddingException("远端Embedding维度必须在64到2048之间");
        }
        if (properties.getBatchSize() < 1 || properties.getBatchSize() > 10) {
            throw new EmbeddingException("远端Embedding批次大小必须在1到10之间");
        }
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
