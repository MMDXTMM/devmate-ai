package com.devmate.generation.entity;

import com.baomidou.mybatisplus.annotation.*;

@TableName("generation_run")
public class GenerationRun {
    @TableId
    private Long id;
    private Long sessionId;
    private Long versionId;
    private String attemptKey;
    private String status;
    private String summary;
    private String runInstructions;
    private String manifestJson;
    private String warningsJson;
    private String errorMessage;
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime updatedAt;
    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long value) { this.sessionId = value; }
    public Long getVersionId() { return versionId; }
    public void setVersionId(Long value) { this.versionId = value; }
    public String getAttemptKey() { return attemptKey; }
    public void setAttemptKey(String value) { this.attemptKey = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    public String getSummary() { return summary; }
    public void setSummary(String value) { this.summary = value; }
    public String getRunInstructions() { return runInstructions; }
    public void setRunInstructions(String value) { this.runInstructions = value; }
    public String getManifestJson() { return manifestJson; }
    public void setManifestJson(String value) { this.manifestJson = value; }
    public String getWarningsJson() { return warningsJson; }
    public void setWarningsJson(String value) { this.warningsJson = value; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String value) { this.errorMessage = value; }
    public java.time.LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.LocalDateTime value) { this.createdAt = value; }
    public java.time.LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(java.time.LocalDateTime value) { this.updatedAt = value; }
}
