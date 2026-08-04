package com.devmate.knowledge.embedding;

import com.devmate.knowledge.config.EmbeddingProperties;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class LocalHashEmbeddingProvider implements EmbeddingProvider {

    private static final Pattern CAMEL_BOUNDARY = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])");
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
    private final int dimensions;

    public LocalHashEmbeddingProvider(EmbeddingProperties properties) {
        this.dimensions = properties.getLocalDimensions();
        if (dimensions < 64 || dimensions > 4096) {
            throw new IllegalArgumentException("本地Embedding维度必须在64到4096之间");
        }
    }

    @Override
    public String providerName() {
        return "LOCAL";
    }

    @Override
    public String modelName() {
        return "code-hash-v1";
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public EmbeddingBatch embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            throw new EmbeddingException("Embedding输入不能为空");
        }
        return new EmbeddingBatch(texts.stream().map(this::embedOne).toList());
    }

    private float[] embedOne(String text) {
        float[] vector = new float[dimensions];
        String normalized = normalize(text);
        List<String> tokens = tokens(normalized);
        for (String token : tokens) {
            addFeature(vector, "t:" + token, 1.0f);
            if (token.length() >= 3) {
                for (int index = 0; index <= token.length() - 3; index++) {
                    addFeature(vector, "g:" + token.substring(index, index + 3), 0.35f);
                }
            }
        }
        normalize(vector);
        return vector;
    }

    private List<String> tokens(String normalized) {
        if (normalized.isBlank()) {
            return List.of("empty");
        }
        List<String> result = new ArrayList<>();
        for (String token : NON_WORD.matcher(normalized).replaceAll(" ").split("\\s+")) {
            if (token.length() > 1) {
                result.add(token);
            }
        }
        return result.isEmpty() ? List.of("empty") : result;
    }

    private String normalize(String value) {
        String safe = value == null ? "" : value;
        String split = CAMEL_BOUNDARY.matcher(safe).replaceAll(" ");
        return Normalizer.normalize(split, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    private void addFeature(float[] vector, String feature, float weight) {
        int hash = feature.hashCode();
        int index = Math.floorMod(hash, vector.length);
        vector[index] += (hash & 1) == 0 ? weight : -weight;
    }

    private void normalize(float[] vector) {
        double norm = 0.0;
        for (float value : vector) {
            norm += value * value;
        }
        if (norm == 0.0) {
            return;
        }
        float divisor = (float) Math.sqrt(norm);
        for (int index = 0; index < vector.length; index++) {
            vector[index] /= divisor;
        }
    }
}
