package com.devmate.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("embedding_index_task")
public class EmbeddingIndexTask {

    @TableId
    private Long id;
    private Long projectId;
    private String revision;
    private String provider;
    private String modelName;
    private Integer dimensions;
    private String status;
    private Integer totalChunks;
    private Integer processedChunks;
    private Integer skippedChunks;
    private Integer failedChunks;
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
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public Integer getDimensions() { return dimensions; }
    public void setDimensions(Integer dimensions) { this.dimensions = dimensions; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getTotalChunks() { return totalChunks; }
    public void setTotalChunks(Integer totalChunks) { this.totalChunks = totalChunks; }
    public Integer getProcessedChunks() { return processedChunks; }
    public void setProcessedChunks(Integer processedChunks) { this.processedChunks = processedChunks; }
    public Integer getSkippedChunks() { return skippedChunks; }
    public void setSkippedChunks(Integer skippedChunks) { this.skippedChunks = skippedChunks; }
    public Integer getFailedChunks() { return failedChunks; }
    public void setFailedChunks(Integer failedChunks) { this.failedChunks = failedChunks; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
}
