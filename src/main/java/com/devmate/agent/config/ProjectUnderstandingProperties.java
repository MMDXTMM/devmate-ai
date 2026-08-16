package com.devmate.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "devmate.project-understanding")
public class ProjectUnderstandingProperties {
    private String promptVersion = "project-understanding-v1";
    private int maxEvidenceChunks = 24;
    private int maxEvidenceCharacters = 24000;
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(90);
    private Duration staleTaskTimeout = Duration.ofMinutes(10);

    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
    public int getMaxEvidenceChunks() { return maxEvidenceChunks; }
    public void setMaxEvidenceChunks(int maxEvidenceChunks) { this.maxEvidenceChunks = maxEvidenceChunks; }
    public int getMaxEvidenceCharacters() { return maxEvidenceCharacters; }
    public void setMaxEvidenceCharacters(int maxEvidenceCharacters) { this.maxEvidenceCharacters = maxEvidenceCharacters; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    public Duration getStaleTaskTimeout() { return staleTaskTimeout; }
    public void setStaleTaskTimeout(Duration staleTaskTimeout) { this.staleTaskTimeout = staleTaskTimeout; }
}
