package com.devmate.review.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("code_review_file")
public class CodeReviewFile {
    @TableId
    private Long id;
    private Long reviewTaskId;
    private Long projectId;
    private String oldPath;
    private String newPath;
    private String newPathHash;
    private String changeType;
    private String coverageStatus;
    private Integer additions;
    private Integer deletions;
    private String baseChangedLinesJson;
    private String changedLinesJson;
    private String mappedSymbolsJson;
    private String skipReason;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getReviewTaskId() { return reviewTaskId; }
    public void setReviewTaskId(Long reviewTaskId) { this.reviewTaskId = reviewTaskId; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getOldPath() { return oldPath; }
    public void setOldPath(String oldPath) { this.oldPath = oldPath; }
    public String getNewPath() { return newPath; }
    public void setNewPath(String newPath) { this.newPath = newPath; }
    public String getNewPathHash() { return newPathHash; }
    public void setNewPathHash(String newPathHash) { this.newPathHash = newPathHash; }
    public String getChangeType() { return changeType; }
    public void setChangeType(String changeType) { this.changeType = changeType; }
    public String getCoverageStatus() { return coverageStatus; }
    public void setCoverageStatus(String coverageStatus) { this.coverageStatus = coverageStatus; }
    public Integer getAdditions() { return additions; }
    public void setAdditions(Integer additions) { this.additions = additions; }
    public Integer getDeletions() { return deletions; }
    public void setDeletions(Integer deletions) { this.deletions = deletions; }
    public String getBaseChangedLinesJson() { return baseChangedLinesJson; }
    public void setBaseChangedLinesJson(String baseChangedLinesJson) { this.baseChangedLinesJson = baseChangedLinesJson; }
    public String getChangedLinesJson() { return changedLinesJson; }
    public void setChangedLinesJson(String changedLinesJson) { this.changedLinesJson = changedLinesJson; }
    public String getMappedSymbolsJson() { return mappedSymbolsJson; }
    public void setMappedSymbolsJson(String mappedSymbolsJson) { this.mappedSymbolsJson = mappedSymbolsJson; }
    public String getSkipReason() { return skipReason; }
    public void setSkipReason(String skipReason) { this.skipReason = skipReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
