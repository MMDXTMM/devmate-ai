package com.devmate.knowledge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "devmate.retrieval")
public class RetrievalProperties {

    private String configVersion = "lexical-graph-v1";
    private int candidateLimit = 1000;
    private int referenceLimit = 5000;
    private int defaultTopK = 8;
    private int defaultTokenBudget = 4000;
    private int previewCharacters = 800;
    private int maxTrimmedDetails = 50;
    private int maxEvaluationCases = 100;

    public String getConfigVersion() {
        return configVersion;
    }

    public void setConfigVersion(String configVersion) {
        this.configVersion = configVersion;
    }

    public int getCandidateLimit() {
        return candidateLimit;
    }

    public void setCandidateLimit(int candidateLimit) {
        this.candidateLimit = candidateLimit;
    }

    public int getReferenceLimit() {
        return referenceLimit;
    }

    public void setReferenceLimit(int referenceLimit) {
        this.referenceLimit = referenceLimit;
    }

    public int getDefaultTopK() {
        return defaultTopK;
    }

    public void setDefaultTopK(int defaultTopK) {
        this.defaultTopK = defaultTopK;
    }

    public int getDefaultTokenBudget() {
        return defaultTokenBudget;
    }

    public void setDefaultTokenBudget(int defaultTokenBudget) {
        this.defaultTokenBudget = defaultTokenBudget;
    }

    public int getPreviewCharacters() {
        return previewCharacters;
    }

    public void setPreviewCharacters(int previewCharacters) {
        this.previewCharacters = previewCharacters;
    }

    public int getMaxTrimmedDetails() {
        return maxTrimmedDetails;
    }

    public void setMaxTrimmedDetails(int maxTrimmedDetails) {
        this.maxTrimmedDetails = maxTrimmedDetails;
    }

    public int getMaxEvaluationCases() {
        return maxEvaluationCases;
    }

    public void setMaxEvaluationCases(int maxEvaluationCases) {
        this.maxEvaluationCases = maxEvaluationCases;
    }
}
