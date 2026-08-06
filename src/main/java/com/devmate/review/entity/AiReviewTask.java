package com.devmate.review.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("ai_review_task")
public class AiReviewTask {
    @TableId
    private Long id;
    private Long projectId;
    private Long reviewTaskId;
    private Long staticAnalysisTaskId;
    private Long invocationId;
    private String attemptKey;
    private String revision;
    private String provider;
    private String modelName;
    private String promptVersion;
    private String executionMode;
    private String retrievalConfigVersion;
    private String retrievalMode;
    private String status;
    private Integer contextChunks;
    private Integer findingCount;
    private Integer rejectedFindings;
    private String runningKey;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getReviewTaskId() { return reviewTaskId; }
    public void setReviewTaskId(Long reviewTaskId) { this.reviewTaskId = reviewTaskId; }
    public Long getStaticAnalysisTaskId() { return staticAnalysisTaskId; }
    public void setStaticAnalysisTaskId(Long staticAnalysisTaskId) { this.staticAnalysisTaskId = staticAnalysisTaskId; }
    public Long getInvocationId() { return invocationId; }
    public void setInvocationId(Long invocationId) { this.invocationId = invocationId; }
    public String getAttemptKey() { return attemptKey; }
    public void setAttemptKey(String attemptKey) { this.attemptKey = attemptKey; }
    public String getRevision() { return revision; }
    public void setRevision(String revision) { this.revision = revision; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
    public String getExecutionMode() { return executionMode; }
    public void setExecutionMode(String executionMode) { this.executionMode = executionMode; }
    public String getRetrievalConfigVersion() { return retrievalConfigVersion; }
    public void setRetrievalConfigVersion(String retrievalConfigVersion) { this.retrievalConfigVersion = retrievalConfigVersion; }
    public String getRetrievalMode() { return retrievalMode; }
    public void setRetrievalMode(String retrievalMode) { this.retrievalMode = retrievalMode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getContextChunks() { return contextChunks; }
    public void setContextChunks(Integer contextChunks) { this.contextChunks = contextChunks; }
    public Integer getFindingCount() { return findingCount; }
    public void setFindingCount(Integer findingCount) { this.findingCount = findingCount; }
    public Integer getRejectedFindings() { return rejectedFindings; }
    public void setRejectedFindings(Integer rejectedFindings) { this.rejectedFindings = rejectedFindings; }
    public String getRunningKey() { return runningKey; }
    public void setRunningKey(String runningKey) { this.runningKey = runningKey; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
}
