package com.devmate.review.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import java.math.BigDecimal;

@TableName("review_finding")
public class ReviewFinding {
    @TableId
    private Long id;
    private Long projectId;
    private Long reviewTaskId;
    private Long analysisTaskId;
    private Long aiReviewTaskId;
    private Long chunkId;
    private String source;
    private String ruleId;
    private String category;
    private String severity;
    private String filePath;
    private String pathHash;
    private Integer startLine;
    private Integer endLine;
    private String message;
    private String evidence;
    private String conclusionType;
    private BigDecimal confidence;
    private String riskScenario;
    private String suggestion;
    private String verification;
    private String fingerprint;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getReviewTaskId() { return reviewTaskId; }
    public void setReviewTaskId(Long reviewTaskId) { this.reviewTaskId = reviewTaskId; }
    public Long getAnalysisTaskId() { return analysisTaskId; }
    public void setAnalysisTaskId(Long analysisTaskId) { this.analysisTaskId = analysisTaskId; }
    public Long getAiReviewTaskId() { return aiReviewTaskId; }
    public void setAiReviewTaskId(Long aiReviewTaskId) { this.aiReviewTaskId = aiReviewTaskId; }
    public Long getChunkId() { return chunkId; }
    public void setChunkId(Long chunkId) { this.chunkId = chunkId; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getPathHash() { return pathHash; }
    public void setPathHash(String pathHash) { this.pathHash = pathHash; }
    public Integer getStartLine() { return startLine; }
    public void setStartLine(Integer startLine) { this.startLine = startLine; }
    public Integer getEndLine() { return endLine; }
    public void setEndLine(Integer endLine) { this.endLine = endLine; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }
    public String getConclusionType() { return conclusionType; }
    public void setConclusionType(String conclusionType) { this.conclusionType = conclusionType; }
    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }
    public String getRiskScenario() { return riskScenario; }
    public void setRiskScenario(String riskScenario) { this.riskScenario = riskScenario; }
    public String getSuggestion() { return suggestion; }
    public void setSuggestion(String suggestion) { this.suggestion = suggestion; }
    public String getVerification() { return verification; }
    public void setVerification(String verification) { this.verification = verification; }
    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
