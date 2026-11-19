package com.devmate.generation.entity;
import com.baomidou.mybatisplus.annotation.*;
@TableName("generation_task")
public class GenerationTask {
    @TableId
    private Long id;
    public Long getId() { return id; }
    public void setId(Long value) { id=value; }
    private Long sessionId;
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long value) { sessionId=value; }
    private String kind;
    public String getKind() { return kind; }
    public void setKind(String value) { kind=value; }
    private Long referenceId;
    public Long getReferenceId() { return referenceId; }
    public void setReferenceId(Long value) { referenceId=value; }
    private String attemptKey;
    public String getAttemptKey() { return attemptKey; }
    public void setAttemptKey(String value) { attemptKey=value; }
    private String status;
    public String getStatus() { return status; }
    public void setStatus(String value) { status=value; }
    private String phase;
    public String getPhase() { return phase; }
    public void setPhase(String value) { phase=value; }
    private Integer modelCalls;
    public Integer getModelCalls() { return modelCalls; }
    public void setModelCalls(Integer value) { modelCalls=value; }
    private Long inputTokens;
    public Long getInputTokens() { return inputTokens; }
    public void setInputTokens(Long value) { inputTokens=value; }
    private Long outputTokens;
    public Long getOutputTokens() { return outputTokens; }
    public void setOutputTokens(Long value) { outputTokens=value; }
    private Integer usageComplete;
    public Integer getUsageComplete() { return usageComplete; }
    public void setUsageComplete(Integer value) { usageComplete=value; }
    private java.time.LocalDateTime createdAt;
    public java.time.LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.LocalDateTime value) { createdAt=value; }
    private java.time.LocalDateTime startedAt;
    public java.time.LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(java.time.LocalDateTime value) { startedAt=value; }
    private java.time.LocalDateTime finishedAt;
    public java.time.LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(java.time.LocalDateTime value) { finishedAt=value; }
    private java.time.LocalDateTime updatedAt;
    public java.time.LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(java.time.LocalDateTime value) { updatedAt=value; }
}
