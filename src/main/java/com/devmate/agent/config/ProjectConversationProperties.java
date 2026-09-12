package com.devmate.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "devmate.project-conversation")
public class ProjectConversationProperties {
    private String promptVersion = "project-conversation-v1";
    private int historyMessages = 12;
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
