package com.devmate.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "devmate.review-agent")
public class ReviewAgentProperties {

    private String promptVersion = "review-agent-v1";
    private int maxToolCalls = 6;
    private int maxRepeatedCalls = 2;
    private int maxEvidenceChunks = 20;
    private int maxEvidenceTokens = 8000;
    private int searchTopK = 6;
    private int searchTokenBudget = 2500;
    private int maxStructureDocuments = 50;
    private int maxStaticFindings = 50;
    private int maxDiffFiles = 50;
    private int maxToolOutputCharacters = 16000;
    private Duration toolTimeout = Duration.ofSeconds(20);

    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
    public int getMaxToolCalls() { return maxToolCalls; }
    public void setMaxToolCalls(int maxToolCalls) { this.maxToolCalls = maxToolCalls; }
    public int getMaxRepeatedCalls() { return maxRepeatedCalls; }
    public void setMaxRepeatedCalls(int maxRepeatedCalls) { this.maxRepeatedCalls = maxRepeatedCalls; }
    public int getMaxEvidenceChunks() { return maxEvidenceChunks; }
    public void setMaxEvidenceChunks(int maxEvidenceChunks) { this.maxEvidenceChunks = maxEvidenceChunks; }
    public int getMaxEvidenceTokens() { return maxEvidenceTokens; }
    public void setMaxEvidenceTokens(int maxEvidenceTokens) { this.maxEvidenceTokens = maxEvidenceTokens; }
    public int getSearchTopK() { return searchTopK; }
    public void setSearchTopK(int searchTopK) { this.searchTopK = searchTopK; }
    public int getSearchTokenBudget() { return searchTokenBudget; }
    public void setSearchTokenBudget(int searchTokenBudget) { this.searchTokenBudget = searchTokenBudget; }
    public int getMaxStructureDocuments() { return maxStructureDocuments; }
    public void setMaxStructureDocuments(int maxStructureDocuments) {
        this.maxStructureDocuments = maxStructureDocuments;
    }
    public int getMaxStaticFindings() { return maxStaticFindings; }
    public void setMaxStaticFindings(int maxStaticFindings) { this.maxStaticFindings = maxStaticFindings; }
    public int getMaxDiffFiles() { return maxDiffFiles; }
    public void setMaxDiffFiles(int maxDiffFiles) { this.maxDiffFiles = maxDiffFiles; }
    public int getMaxToolOutputCharacters() { return maxToolOutputCharacters; }
    public void setMaxToolOutputCharacters(int maxToolOutputCharacters) {
        this.maxToolOutputCharacters = maxToolOutputCharacters;
    }
    public Duration getToolTimeout() { return toolTimeout; }
    public void setToolTimeout(Duration toolTimeout) { this.toolTimeout = toolTimeout; }
}
