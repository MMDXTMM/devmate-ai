package com.devmate.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("index_task")
public class IndexTask {

    @TableId
    private Long id;
    private Long projectId;
    private String taskType;
    private String revision;
    private String structureVersion;
    private String status;
    private Integer totalFiles;
    private Integer processedFiles;
    private Integer reusedFiles;
    private Integer failedFiles;
    private Long cloneDurationMs;
    private Long scanDurationMs;
    private Long planDurationMs;
    private Long parseDurationMs;
    private Long persistDurationMs;
    private Long totalDurationMs;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public String getRevision() { return revision; }
    public void setRevision(String revision) { this.revision = revision; }
    public String getStructureVersion() { return structureVersion; }
    public void setStructureVersion(String structureVersion) { this.structureVersion = structureVersion; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getTotalFiles() { return totalFiles; }
    public void setTotalFiles(Integer totalFiles) { this.totalFiles = totalFiles; }
    public Integer getProcessedFiles() { return processedFiles; }
    public void setProcessedFiles(Integer processedFiles) { this.processedFiles = processedFiles; }
    public Integer getReusedFiles() { return reusedFiles; }
    public void setReusedFiles(Integer reusedFiles) { this.reusedFiles = reusedFiles; }
    public Integer getFailedFiles() { return failedFiles; }
    public void setFailedFiles(Integer failedFiles) { this.failedFiles = failedFiles; }
    public Long getCloneDurationMs() { return cloneDurationMs; }
    public void setCloneDurationMs(Long cloneDurationMs) { this.cloneDurationMs = cloneDurationMs; }
    public Long getScanDurationMs() { return scanDurationMs; }
    public void setScanDurationMs(Long scanDurationMs) { this.scanDurationMs = scanDurationMs; }
    public Long getPlanDurationMs() { return planDurationMs; }
    public void setPlanDurationMs(Long planDurationMs) { this.planDurationMs = planDurationMs; }
    public Long getParseDurationMs() { return parseDurationMs; }
    public void setParseDurationMs(Long parseDurationMs) { this.parseDurationMs = parseDurationMs; }
    public Long getPersistDurationMs() { return persistDurationMs; }
    public void setPersistDurationMs(Long persistDurationMs) { this.persistDurationMs = persistDurationMs; }
    public Long getTotalDurationMs() { return totalDurationMs; }
    public void setTotalDurationMs(Long totalDurationMs) { this.totalDurationMs = totalDurationMs; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
}
