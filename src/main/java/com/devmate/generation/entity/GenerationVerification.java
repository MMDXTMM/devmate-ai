package com.devmate.generation.entity;
import com.baomidou.mybatisplus.annotation.*;
@TableName("generation_verification")
public class GenerationVerification {
    @TableId
    private Long id;
    private Long revisionId;
    private Long sessionId;
    private String attemptKey;
    private String status;
    private String profile;
    private String resultJson;
    private Long runningKey;
    public Long getRunningKey() { return runningKey; }
    public void setRunningKey(Long value) { runningKey=value; }
    private Integer repairLimit;
    private Integer repairs;
    private Long resultRevisionId;
    private String errorMessage;
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime updatedAt;
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getRevisionId() { return revisionId; }
    public void setRevisionId(Long value) { revisionId = value; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long value) { sessionId = value; }
    public String getAttemptKey() { return attemptKey; }
    public void setAttemptKey(String value) { attemptKey = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public String getProfile() { return profile; }
    public void setProfile(String value) { profile = value; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String value) { resultJson = value; }
    public Integer getRepairLimit() { return repairLimit; }
    public void setRepairLimit(Integer value) { repairLimit = value; }
    public Integer getRepairs() { return repairs; }
    public void setRepairs(Integer value) { repairs = value; }
    public Long getResultRevisionId() { return resultRevisionId; }
    public void setResultRevisionId(Long value) { resultRevisionId = value; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String value) { errorMessage = value; }
    public java.time.LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.LocalDateTime value) { createdAt = value; }
    public java.time.LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(java.time.LocalDateTime value) { updatedAt = value; }
}
