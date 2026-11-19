package com.devmate.generation.entity;
import com.baomidou.mybatisplus.annotation.*;
@TableName("generation_revision")
public class GenerationRevision {
    @TableId
    private Long id;
    private Long runningKey;
    public Long getRunningKey() { return runningKey; }
    public void setRunningKey(Long value) { runningKey=value; }
    private Long sessionId;
    private Long runId;
    private Long rootRunId;
    private Long parentId;
    private String attemptKey;
    private String kind;
    private String status;
    private String requestText;
    private String summary;
    private String runInstructions;
    private String manifestJson;
    private String warningsJson;
    private String errorMessage;
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime updatedAt;
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long value) { sessionId = value; }
    public Long getRunId() { return runId; }
    public void setRunId(Long value) { runId = value; }
    public Long getRootRunId() { return rootRunId; }
    public void setRootRunId(Long value) { rootRunId = value; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long value) { parentId = value; }
    public String getAttemptKey() { return attemptKey; }
    public void setAttemptKey(String value) { attemptKey = value; }
    public String getKind() { return kind; }
    public void setKind(String value) { kind = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public String getRequestText() { return requestText; }
    public void setRequestText(String value) { requestText = value; }
    public String getSummary() { return summary; }
    public void setSummary(String value) { summary = value; }
    public String getRunInstructions() { return runInstructions; }
    public void setRunInstructions(String value) { runInstructions = value; }
    public String getManifestJson() { return manifestJson; }
    public void setManifestJson(String value) { manifestJson = value; }
    public String getWarningsJson() { return warningsJson; }
    public void setWarningsJson(String value) { warningsJson = value; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String value) { errorMessage = value; }
    public java.time.LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.LocalDateTime value) { createdAt = value; }
    public java.time.LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(java.time.LocalDateTime value) { updatedAt = value; }
}
