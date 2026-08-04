package com.devmate.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "devmate.ai-review")
public class AiReviewProperties {

    private String provider = "DASHSCOPE";
    private String model = "qwen-plus";
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private String apiKey;
    private String promptVersion = "ai-review-v1";
    private int topK = 12;
    private int tokenBudget = 6000;
    private int maxFindings = 30;
    private int maxPromptCharacters = 60000;
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(90);
    private Duration staleTaskTimeout = Duration.ofMinutes(10);

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
    public int getTopK() { return topK; }
    public void setTopK(int topK) { this.topK = topK; }
    public int getTokenBudget() { return tokenBudget; }
    public void setTokenBudget(int tokenBudget) { this.tokenBudget = tokenBudget; }
    public int getMaxFindings() { return maxFindings; }
    public void setMaxFindings(int maxFindings) { this.maxFindings = maxFindings; }
    public int getMaxPromptCharacters() { return maxPromptCharacters; }
    public void setMaxPromptCharacters(int maxPromptCharacters) { this.maxPromptCharacters = maxPromptCharacters; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    public Duration getStaleTaskTimeout() { return staleTaskTimeout; }
    public void setStaleTaskTimeout(Duration staleTaskTimeout) { this.staleTaskTimeout = staleTaskTimeout; }
}
