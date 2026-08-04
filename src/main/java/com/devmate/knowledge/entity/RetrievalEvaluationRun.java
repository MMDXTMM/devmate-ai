package com.devmate.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("retrieval_evaluation_run")
public class RetrievalEvaluationRun {

    @TableId
    private Long id;
    private Long projectId;
    private String revision;
    private String datasetVersion;
    private String retrievalConfigVersion;
    private String retrievalMode;
    private String status;
    private Integer totalCases;
    private Integer resolvedCases;
    private BigDecimal recallAtK;
    private BigDecimal precisionAtK;
    private BigDecimal hitRateAtK;
    private BigDecimal meanReciprocalRank;
    private String resultJson;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getRevision() { return revision; }
    public void setRevision(String revision) { this.revision = revision; }
    public String getDatasetVersion() { return datasetVersion; }
    public void setDatasetVersion(String datasetVersion) { this.datasetVersion = datasetVersion; }
    public String getRetrievalConfigVersion() { return retrievalConfigVersion; }
    public void setRetrievalConfigVersion(String retrievalConfigVersion) { this.retrievalConfigVersion = retrievalConfigVersion; }
    public String getRetrievalMode() { return retrievalMode; }
    public void setRetrievalMode(String retrievalMode) { this.retrievalMode = retrievalMode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getTotalCases() { return totalCases; }
    public void setTotalCases(Integer totalCases) { this.totalCases = totalCases; }
    public Integer getResolvedCases() { return resolvedCases; }
    public void setResolvedCases(Integer resolvedCases) { this.resolvedCases = resolvedCases; }
    public BigDecimal getRecallAtK() { return recallAtK; }
    public void setRecallAtK(BigDecimal recallAtK) { this.recallAtK = recallAtK; }
    public BigDecimal getPrecisionAtK() { return precisionAtK; }
    public void setPrecisionAtK(BigDecimal precisionAtK) { this.precisionAtK = precisionAtK; }
    public BigDecimal getHitRateAtK() { return hitRateAtK; }
    public void setHitRateAtK(BigDecimal hitRateAtK) { this.hitRateAtK = hitRateAtK; }
    public BigDecimal getMeanReciprocalRank() { return meanReciprocalRank; }
    public void setMeanReciprocalRank(BigDecimal meanReciprocalRank) { this.meanReciprocalRank = meanReciprocalRank; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
}
