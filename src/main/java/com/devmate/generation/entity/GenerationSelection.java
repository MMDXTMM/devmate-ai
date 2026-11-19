package com.devmate.generation.entity;
import com.baomidou.mybatisplus.annotation.*;
@TableName("generation_selection")
public class GenerationSelection {
    @TableId
    private Long runId;
    public Long getRunId() { return runId; }
    public void setRunId(Long value) { runId=value; }
    private Long revisionId;
    public Long getRevisionId() { return revisionId; }
    public void setRevisionId(Long value) { revisionId=value; }
    private java.time.LocalDateTime updatedAt;
    public java.time.LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(java.time.LocalDateTime value) { updatedAt=value; }
}
