package com.devmate.generation.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("generation_spec_version")
public class GenerationSpecVersion {

    @TableId
    private Long id;
    private Long sessionId;
    private Integer versionNo;
    private String requirementSummary;
    private String architectureSummary;
    private String assumptionsJson;
    private String questionsJson;
    private String answersJson;
    private String status;
    private String promptVersion;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public String getRequirementSummary() { return requirementSummary; }
    public void setRequirementSummary(String requirementSummary) { this.requirementSummary = requirementSummary; }
    public String getArchitectureSummary() { return architectureSummary; }
    public void setArchitectureSummary(String architectureSummary) { this.architectureSummary = architectureSummary; }
    public String getAssumptionsJson() { return assumptionsJson; }
    public void setAssumptionsJson(String assumptionsJson) { this.assumptionsJson = assumptionsJson; }
    public String getQuestionsJson() { return questionsJson; }
    public void setQuestionsJson(String questionsJson) { this.questionsJson = questionsJson; }
    public String getAnswersJson() { return answersJson; }
    public void setAnswersJson(String answersJson) { this.answersJson = answersJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
