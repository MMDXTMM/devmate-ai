package com.devmate.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "devmate.project-conversation")
public class ProjectConversationProperties {
    private String promptVersion = "project-conversation-v4";
    private int historyMessages = 12;
    private int maxResearchCalls = 3;
    private int maxEvidenceCharacters = 24000;
    private int maxHistoryCharacters = 20000;
    private Duration researchTimeout = Duration.ofSeconds(35);
    public int getMaxResearchCalls() { return maxResearchCalls; }
    public void setMaxResearchCalls(int value) { maxResearchCalls = value; }
    public int getMaxEvidenceCharacters() { return maxEvidenceCharacters; }
    public void setMaxEvidenceCharacters(int value) { maxEvidenceCharacters = value; }
    public int getMaxHistoryCharacters() { return maxHistoryCharacters; }
    public void setMaxHistoryCharacters(int value) { maxHistoryCharacters = value; }
    public Duration getResearchTimeout() { return researchTimeout; }
    public void setResearchTimeout(Duration value) { researchTimeout = value; }
    private int topK = 6;
    private int tokenBudget = 5000;
    private int maxAnswerCharacters = 16000;
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(90);

    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
    public int getHistoryMessages() { return historyMessages; }
    public void setHistoryMessages(int historyMessages) { this.historyMessages = historyMessages; }
    public int getTopK() { return topK; }
    public void setTopK(int topK) { this.topK = topK; }
    public int getTokenBudget() { return tokenBudget; }
    public void setTokenBudget(int tokenBudget) { this.tokenBudget = tokenBudget; }
    public int getMaxAnswerCharacters() { return maxAnswerCharacters; }
    public void setMaxAnswerCharacters(int maxAnswerCharacters) { this.maxAnswerCharacters = maxAnswerCharacters; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
}
