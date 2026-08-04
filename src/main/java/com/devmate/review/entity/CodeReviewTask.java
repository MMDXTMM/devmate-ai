package com.devmate.review.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("code_review_task")
public class CodeReviewTask {
    @TableId
    private Long id;
    private Long projectId;
    private Long indexTaskId;
    private String baseRevision;
    private String targetRevision;
    private String triggerType;
    private String status;
    private Integer changedFiles;
    private Integer fullyMappedFiles;
    private Integer partiallyMappedFiles;
    private Integer skippedFiles;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getIndexTaskId() { return indexTaskId; }
    public void setIndexTaskId(Long indexTaskId) { this.indexTaskId = indexTaskId; }
    public String getBaseRevision() { return baseRevision; }
    public void setBaseRevision(String baseRevision) { this.baseRevision = baseRevision; }
    public String getTargetRevision() { return targetRevision; }
    public void setTargetRevision(String targetRevision) { this.targetRevision = targetRevision; }
    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getChangedFiles() { return changedFiles; }
    public void setChangedFiles(Integer changedFiles) { this.changedFiles = changedFiles; }
    public Integer getFullyMappedFiles() { return fullyMappedFiles; }
    public void setFullyMappedFiles(Integer fullyMappedFiles) { this.fullyMappedFiles = fullyMappedFiles; }
    public Integer getPartiallyMappedFiles() { return partiallyMappedFiles; }
    public void setPartiallyMappedFiles(Integer partiallyMappedFiles) { this.partiallyMappedFiles = partiallyMappedFiles; }
    public Integer getSkippedFiles() { return skippedFiles; }
    public void setSkippedFiles(Integer skippedFiles) { this.skippedFiles = skippedFiles; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
}
