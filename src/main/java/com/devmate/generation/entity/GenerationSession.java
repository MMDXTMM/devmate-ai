package com.devmate.generation.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("generation_session")
public class GenerationSession {

    @TableId
    private Long id;
    private Long ownerId;
    private String originalRequirement;
    private String status;
    private Integer latestVersionNo;
    private Long confirmedVersionId;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public String getOriginalRequirement() { return originalRequirement; }
    public void setOriginalRequirement(String originalRequirement) { this.originalRequirement = originalRequirement; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getLatestVersionNo() { return latestVersionNo; }
    public void setLatestVersionNo(Integer latestVersionNo) { this.latestVersionNo = latestVersionNo; }
    public Long getConfirmedVersionId() { return confirmedVersionId; }
    public void setConfirmedVersionId(Long confirmedVersionId) { this.confirmedVersionId = confirmedVersionId; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
