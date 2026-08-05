package com.devmate.tool.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("tool_call_log")
public class ToolCallLog {
    @TableId
    private Long id;
    private Long invocationId;
    private Long projectId;
    private String toolCallId;
    private Integer stepNo;
    private String toolName;
    private String argumentsHash;
    private String argumentsSummary;
    private String resultSummary;
    private String status;
    private Long latencyMs;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getInvocationId() { return invocationId; }
    public void setInvocationId(Long invocationId) { this.invocationId = invocationId; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getToolCallId() { return toolCallId; }
    public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }
    public Integer getStepNo() { return stepNo; }
    public void setStepNo(Integer stepNo) { this.stepNo = stepNo; }
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public String getArgumentsHash() { return argumentsHash; }
    public void setArgumentsHash(String argumentsHash) { this.argumentsHash = argumentsHash; }
    public String getArgumentsSummary() { return argumentsSummary; }
    public void setArgumentsSummary(String argumentsSummary) { this.argumentsSummary = argumentsSummary; }
    public String getResultSummary() { return resultSummary; }
    public void setResultSummary(String resultSummary) { this.resultSummary = resultSummary; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Long latencyMs) { this.latencyMs = latencyMs; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
