package com.devmate.generation.entity;
import com.baomidou.mybatisplus.annotation.*;
@TableName("generation_insight")
public class GenerationInsight {
    @TableId
    private Long id;
    public Long getId() { return id; }
    public void setId(Long value) { id=value; }
    private Long sessionId;
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long value) { sessionId=value; }
    private Long revisionId;
    public Long getRevisionId() { return revisionId; }
    public void setRevisionId(Long value) { revisionId=value; }
    private String kind;
    public String getKind() { return kind; }
    public void setKind(String value) { kind=value; }
    private String attemptKey;
    public String getAttemptKey() { return attemptKey; }
    public void setAttemptKey(String value) { attemptKey=value; }
    private String runningKey;
    public String getRunningKey() { return runningKey; }
    public void setRunningKey(String value) { runningKey=value; }
    private String status;
    public String getStatus() { return status; }
    public void setStatus(String value) { status=value; }
    private String requestText;
    public String getRequestText() { return requestText; }
    public void setRequestText(String value) { requestText=value; }
    private String outputJson;
    public String getOutputJson() { return outputJson; }
    public void setOutputJson(String value) { outputJson=value; }
    private String errorMessage;
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String value) { errorMessage=value; }
    private java.time.LocalDateTime createdAt;
    public java.time.LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.LocalDateTime value) { createdAt=value; }
    private java.time.LocalDateTime updatedAt;
    public java.time.LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(java.time.LocalDateTime value) { updatedAt=value; }
}
