package com.devmate.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("code_reference")
public class CodeReference {

    @TableId
    private Long id;
    private Long projectId;
    private Long sourceChunkId;
    private Long targetChunkId;
    private String revision;
    private String referenceKind;
    private String referenceName;
    private String qualifier;
    private Integer argumentCount;
    private Integer startLine;
    private Integer endLine;
    private String metadataJson;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getSourceChunkId() { return sourceChunkId; }
    public void setSourceChunkId(Long sourceChunkId) { this.sourceChunkId = sourceChunkId; }
    public Long getTargetChunkId() { return targetChunkId; }
    public void setTargetChunkId(Long targetChunkId) { this.targetChunkId = targetChunkId; }
    public String getRevision() { return revision; }
    public void setRevision(String revision) { this.revision = revision; }
    public String getReferenceKind() { return referenceKind; }
    public void setReferenceKind(String referenceKind) { this.referenceKind = referenceKind; }
    public String getReferenceName() { return referenceName; }
    public void setReferenceName(String referenceName) { this.referenceName = referenceName; }
    public String getQualifier() { return qualifier; }
    public void setQualifier(String qualifier) { this.qualifier = qualifier; }
    public Integer getArgumentCount() { return argumentCount; }
    public void setArgumentCount(Integer argumentCount) { this.argumentCount = argumentCount; }
    public Integer getStartLine() { return startLine; }
    public void setStartLine(Integer startLine) { this.startLine = startLine; }
    public Integer getEndLine() { return endLine; }
    public void setEndLine(Integer endLine) { this.endLine = endLine; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
