package com.devmate.review.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("review_workflow_run")
public class ReviewWorkflowRun {
    @TableId
    private Long id;
    private Long projectId;
    private String attemptKey;
    private String status;
    private String currentStage;
    private Long indexTaskId;
    private Long reviewTaskId;
    private Long staticAnalysisTaskId;
    private Long embeddingTaskId;
    private Long aiReviewTaskId;
    private String runningKey;
    private String errorMessage;
    private String recoveryAction;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getAttemptKey() { return attemptKey; }
    public void setAttemptKey(String attemptKey) { this.attemptKey = attemptKey; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCurrentStage() { return currentStage; }
    public void setCurrentStage(String currentStage) { this.currentStage = currentStage; }
    public Long getIndexTaskId() { return indexTaskId; }
    public void setIndexTaskId(Long indexTaskId) { this.indexTaskId = indexTaskId; }
    public Long getReviewTaskId() { return reviewTaskId; }
    public void setReviewTaskId(Long reviewTaskId) { this.reviewTaskId = reviewTaskId; }
    public Long getStaticAnalysisTaskId() { return staticAnalysisTaskId; }
    public void setStaticAnalysisTaskId(Long staticAnalysisTaskId) { this.staticAnalysisTaskId = staticAnalysisTaskId; }
    public Long getEmbeddingTaskId() { return embeddingTaskId; }
    public void setEmbeddingTaskId(Long embeddingTaskId) { this.embeddingTaskId = embeddingTaskId; }
    public Long getAiReviewTaskId() { return aiReviewTaskId; }
    public void setAiReviewTaskId(Long aiReviewTaskId) { this.aiReviewTaskId = aiReviewTaskId; }
    public String getRunningKey() { return runningKey; }
    public void setRunningKey(String runningKey) { this.runningKey = runningKey; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getRecoveryAction() { return recoveryAction; }
    public void setRecoveryAction(String recoveryAction) { this.recoveryAction = recoveryAction; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
}
