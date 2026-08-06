package com.devmate.knowledge.embedding;

import com.devmate.knowledge.config.EmbeddingProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Comparator;
import java.util.List;

@Component
public class DashScopeEmbeddingProvider implements EmbeddingProvider {

    private final EmbeddingProperties properties;
    private final RestClient.Builder restClientBuilder;

    public DashScopeEmbeddingProvider(
            EmbeddingProperties properties,
            RestClient.Builder restClientBuilder
    ) {
        this.properties = properties;
        this.restClientBuilder = restClientBuilder;
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
            EmbeddingResponse response = restClientBuilder
                    .baseUrl(trimTrailingSlash(properties.getBaseUrl()))
                    .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                    .build()
                    .post()
                    .uri("/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new EmbeddingRequest(
                            properties.getModel(),
                            texts,
                            properties.getDimensions(),
                            "float"
                    ))
                    .retrieve()
                    .body(EmbeddingResponse.class);
            if (response == null || response.data() == null || response.data().size() != texts.size()) {
                throw new EmbeddingException("远端Embedding返回数量不一致");
            }
            List<float[]> vectors = response.data().stream()
                    .sorted(Comparator.comparingInt(EmbeddingData::index))
                    .map(this::toVector)
                    .toList();
            return new EmbeddingBatch(vectors);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 429) {
                throw new EmbeddingException("远端Embedding请求过于频繁或额度不足，请稍后重试并检查额度", exception);
            }
            throw new EmbeddingException("远端Embedding调用失败", exception);
        } catch (RestClientException exception) {
            throw new EmbeddingException("远端Embedding调用失败", exception);
        }
    }

    private float[] toVector(EmbeddingData data) {
        if (data.embedding() == null || data.embedding().size() != dimensions()) {
            throw new EmbeddingException("远端Embedding维度不符合配置");
        }
        float[] vector = new float[data.embedding().size()];
        for (int index = 0; index < data.embedding().size(); index++) {
            vector[index] = data.embedding().get(index).floatValue();
        }
        return vector;
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

    record EmbeddingRequest(
            String model,
            List<String> input,
            int dimensions,
            @JsonProperty("encoding_format")
            String encodingFormat
    ) {
    }

    record EmbeddingResponse(List<EmbeddingData> data, String model) {
    }

    record EmbeddingData(int index, List<Double> embedding) {
    }
}
