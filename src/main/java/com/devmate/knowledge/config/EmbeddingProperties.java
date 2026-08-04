package com.devmate.knowledge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "devmate.embedding")
public class EmbeddingProperties {

    private String provider = "LOCAL";
    private String model = "text-embedding-v4";
    private String baseUrl;
    private String apiKey;
    private int dimensions = 1024;
    private int localDimensions = 256;
    private int batchSize = 10;
    private int maxInputCharacters = 16000;
    private int maxIndexChunks = 5000;
    private int maxVectorScan = 5000;
    private double minimumSimilarity = 0.05;

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public int getDimensions() { return dimensions; }
    public void setDimensions(int dimensions) { this.dimensions = dimensions; }
    public int getLocalDimensions() { return localDimensions; }
    public void setLocalDimensions(int localDimensions) { this.localDimensions = localDimensions; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public int getMaxInputCharacters() { return maxInputCharacters; }
    public void setMaxInputCharacters(int maxInputCharacters) { this.maxInputCharacters = maxInputCharacters; }
    public int getMaxIndexChunks() { return maxIndexChunks; }
    public void setMaxIndexChunks(int maxIndexChunks) { this.maxIndexChunks = maxIndexChunks; }
    public int getMaxVectorScan() { return maxVectorScan; }
    public void setMaxVectorScan(int maxVectorScan) { this.maxVectorScan = maxVectorScan; }
    public double getMinimumSimilarity() { return minimumSimilarity; }
    public void setMinimumSimilarity(double minimumSimilarity) { this.minimumSimilarity = minimumSimilarity; }
}
