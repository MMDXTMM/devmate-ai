package com.devmate.generation.entity;
import com.baomidou.mybatisplus.annotation.*;
@TableName("generation_change_plan")
public class GenerationChangePlan {
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
    private String attemptKey;
    public String getAttemptKey() { return attemptKey; }
    public void setAttemptKey(String value) { attemptKey=value; }
    private Long runningKey;
    public Long getRunningKey() { return runningKey; }
    public void setRunningKey(Long value) { runningKey=value; }
    private String requestText;
    public String getRequestText() { return requestText; }
    public void setRequestText(String value) { requestText=value; }
    private String status;
    public String getStatus() { return status; }
    public void setStatus(String value) { status=value; }
    private String planJson;
    public String getPlanJson() { return planJson; }
    public void setPlanJson(String value) { planJson=value; }
    private Long resultRevisionId;
    public Long getResultRevisionId() { return resultRevisionId; }
    public void setResultRevisionId(Long value) { resultRevisionId=value; }
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
