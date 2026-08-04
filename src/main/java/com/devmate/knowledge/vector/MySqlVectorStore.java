package com.devmate.knowledge.vector;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devmate.knowledge.config.EmbeddingProperties;
import com.devmate.knowledge.embedding.EmbeddingException;
import com.devmate.knowledge.entity.EmbeddingVector;
import com.devmate.knowledge.mapper.EmbeddingVectorMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class MySqlVectorStore {

    private final EmbeddingVectorMapper vectorMapper;
    private final EmbeddingProperties properties;
    private final ObjectMapper objectMapper;

    public MySqlVectorStore(
            EmbeddingVectorMapper vectorMapper,
            EmbeddingProperties properties,
            ObjectMapper objectMapper
    ) {
        this.vectorMapper = vectorMapper;
        this.properties = properties;
        this.objectMapper = objectMapper;
        if (properties.getMaxVectorScan() < 1) {
            throw new IllegalArgumentException("向量扫描上限必须大于0");
        }
    }

    public VectorSearchResult search(
            Long projectId,
            String revision,
            String provider,
            String model,
            int dimensions,
            float[] queryVector,
            int limit
    ) {
        List<EmbeddingVector> loaded = vectorMapper.selectList(Wrappers.lambdaQuery(EmbeddingVector.class)
                .eq(EmbeddingVector::getProjectId, projectId)
                .eq(EmbeddingVector::getRevision, revision)
                .eq(EmbeddingVector::getProvider, provider)
                .eq(EmbeddingVector::getModelName, model)
                .eq(EmbeddingVector::getDimensions, dimensions)
                .orderByAsc(EmbeddingVector::getVectorId)
                .last("LIMIT " + (properties.getMaxVectorScan() + 1)));
        if (loaded.isEmpty()) {
            return VectorSearchResult.unavailable("当前项目版本尚未使用所选模型建立向量索引");
        }
        boolean limitReached = loaded.size() > properties.getMaxVectorScan();
        List<VectorMatch> matches = new ArrayList<>();
        for (EmbeddingVector stored : loaded.stream().limit(properties.getMaxVectorScan()).toList()) {
            float[] vector = readVector(stored.getVectorJson(), dimensions);
            double similarity = cosine(queryVector, vector);
            if (similarity >= properties.getMinimumSimilarity()) {
                matches.add(new VectorMatch(stored.getChunkId(), round(similarity)));
            }
        }
        matches.sort(Comparator.comparingDouble(VectorMatch::similarity).reversed()
                .thenComparing(VectorMatch::chunkId));
        return new VectorSearchResult(
                matches.stream().limit(limit).toList(),
                Math.min(loaded.size(), properties.getMaxVectorScan()),
                limitReached,
                true,
                null
        );
    }

    public long count(
            Long projectId,
            String revision,
            String provider,
            String model,
            int dimensions
    ) {
        return vectorMapper.selectCount(Wrappers.lambdaQuery(EmbeddingVector.class)
                .eq(EmbeddingVector::getProjectId, projectId)
                .eq(EmbeddingVector::getRevision, revision)
                .eq(EmbeddingVector::getProvider, provider)
                .eq(EmbeddingVector::getModelName, model)
                .eq(EmbeddingVector::getDimensions, dimensions));
    }

    public String writeVector(float[] vector) {
        try {
            return objectMapper.writeValueAsString(vector);
        } catch (JsonProcessingException exception) {
            throw new EmbeddingException("向量序列化失败", exception);
        }
    }

    private float[] readVector(String json, int expectedDimensions) {
        try {
            List<Float> values = objectMapper.readValue(json, new TypeReference<>() { });
            if (values.size() != expectedDimensions) {
                throw new EmbeddingException("已保存向量维度不一致");
            }
            float[] result = new float[values.size()];
            for (int index = 0; index < values.size(); index++) {
                result[index] = values.get(index);
            }
            return result;
        } catch (JsonProcessingException exception) {
            throw new EmbeddingException("读取已保存向量失败", exception);
        }
    }

    private double cosine(float[] left, float[] right) {
        if (left.length != right.length) {
            throw new EmbeddingException("查询向量与索引向量维度不一致");
        }
        double dot = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (int index = 0; index < left.length; index++) {
            dot += left[index] * right[index];
            leftNorm += left[index] * left[index];
            rightNorm += right[index] * right[index];
        }
        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private double round(double value) {
        return Math.round(value * 1_000_000.0) / 1_000_000.0;
    }
}
