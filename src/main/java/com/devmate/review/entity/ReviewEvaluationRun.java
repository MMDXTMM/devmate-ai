package com.devmate.review.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("review_evaluation_run")
public class ReviewEvaluationRun {
    @TableId
    private Long id;
    private Long projectId;
    private Long reviewTaskId;
    private Long aiReviewTaskId;
    private String datasetVersion;
    private String datasetHash;
    private String executionMode;
    private String revision;
    private String modelName;
    private String promptVersion;
    private String retrievalConfigVersion;
    private String status;
    private Integer expectedDefects;
    private Integer predictedFindings;
    private Integer truePositives;
    private Integer falsePositives;
    private Integer falseNegatives;
    private Integer manualReviewCount;
    private Integer partialMetrics;
    private BigDecimal precisionScore;
    private BigDecimal recallScore;
    private BigDecimal f1Score;
    private Integer totalTokens;
    private Long latencyMs;
    private Integer toolCallCount;
    private Integer toolSuccessCount;
    private String resultJson;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getReviewTaskId() { return reviewTaskId; }
    public void setReviewTaskId(Long reviewTaskId) { this.reviewTaskId = reviewTaskId; }
    public Long getAiReviewTaskId() { return aiReviewTaskId; }
    public void setAiReviewTaskId(Long aiReviewTaskId) { this.aiReviewTaskId = aiReviewTaskId; }
    public String getDatasetVersion() { return datasetVersion; }
    public void setDatasetVersion(String datasetVersion) { this.datasetVersion = datasetVersion; }
    public String getDatasetHash() { return datasetHash; }
    public void setDatasetHash(String datasetHash) { this.datasetHash = datasetHash; }
    public String getExecutionMode() { return executionMode; }
    public void setExecutionMode(String executionMode) { this.executionMode = executionMode; }
    public String getRevision() { return revision; }
    public void setRevision(String revision) { this.revision = revision; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
    public String getRetrievalConfigVersion() { return retrievalConfigVersion; }
    public void setRetrievalConfigVersion(String retrievalConfigVersion) { this.retrievalConfigVersion = retrievalConfigVersion; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getExpectedDefects() { return expectedDefects; }
    public void setExpectedDefects(Integer expectedDefects) { this.expectedDefects = expectedDefects; }
    public Integer getPredictedFindings() { return predictedFindings; }
    public void setPredictedFindings(Integer predictedFindings) { this.predictedFindings = predictedFindings; }
    public Integer getTruePositives() { return truePositives; }
    public void setTruePositives(Integer truePositives) { this.truePositives = truePositives; }
    public Integer getFalsePositives() { return falsePositives; }
    public void setFalsePositives(Integer falsePositives) { this.falsePositives = falsePositives; }
    public Integer getFalseNegatives() { return falseNegatives; }
    public void setFalseNegatives(Integer falseNegatives) { this.falseNegatives = falseNegatives; }
    public Integer getManualReviewCount() { return manualReviewCount; }
    public void setManualReviewCount(Integer manualReviewCount) { this.manualReviewCount = manualReviewCount; }
    public Integer getPartialMetrics() { return partialMetrics; }
    public void setPartialMetrics(Integer partialMetrics) { this.partialMetrics = partialMetrics; }
    public BigDecimal getPrecisionScore() { return precisionScore; }
    public void setPrecisionScore(BigDecimal precisionScore) { this.precisionScore = precisionScore; }
    public BigDecimal getRecallScore() { return recallScore; }
    public void setRecallScore(BigDecimal recallScore) { this.recallScore = recallScore; }
    public BigDecimal getF1Score() { return f1Score; }
    public void setF1Score(BigDecimal f1Score) { this.f1Score = f1Score; }
    public Integer getTotalTokens() { return totalTokens; }
    public void setTotalTokens(Integer totalTokens) { this.totalTokens = totalTokens; }
    public Long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Long latencyMs) { this.latencyMs = latencyMs; }
    public Integer getToolCallCount() { return toolCallCount; }
    public void setToolCallCount(Integer toolCallCount) { this.toolCallCount = toolCallCount; }
    public Integer getToolSuccessCount() { return toolSuccessCount; }
    public void setToolSuccessCount(Integer toolSuccessCount) { this.toolSuccessCount = toolSuccessCount; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
}
