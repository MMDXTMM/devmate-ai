package com.devmate.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("retrieval_evaluation_case")
public class RetrievalEvaluationCase {

    @TableId
    private Long id;
    private Long projectId;
    private String datasetVersion;
    private String name;
    private String queryText;
    private String expectedFilePath;
    private String expectedSymbolName;
    private Integer topK;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getDatasetVersion() { return datasetVersion; }
    public void setDatasetVersion(String datasetVersion) { this.datasetVersion = datasetVersion; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getQueryText() { return queryText; }
    public void setQueryText(String queryText) { this.queryText = queryText; }
    public String getExpectedFilePath() { return expectedFilePath; }
    public void setExpectedFilePath(String expectedFilePath) { this.expectedFilePath = expectedFilePath; }
    public String getExpectedSymbolName() { return expectedSymbolName; }
    public void setExpectedSymbolName(String expectedSymbolName) { this.expectedSymbolName = expectedSymbolName; }
    public Integer getTopK() { return topK; }
    public void setTopK(Integer topK) { this.topK = topK; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
