package com.devmate.project.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.devmate.project.entity.Project;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "项目详情；BIGINT 标识在 JSON 中统一使用字符串")
public record ProjectResponse(
        @Schema(description = "项目 ID", type = "string", example = "2084116785588305922",
                pattern = "^[1-9][0-9]*$")
        @JsonSerialize(using = ToStringSerializer.class)
        Long id,
        @Schema(description = "项目名称", example = "devmate-ai")
        String name,
        @Schema(description = "项目用途说明")
        String description,
        @Schema(description = "源码来源", allowableValues = {"LOCAL", "GIT", "UPLOAD"})
        String sourceType,
        @Schema(description = "源码位置")
        String sourceLocation,
        @Schema(description = "默认分支", example = "main")
        String defaultBranch,
        @Schema(description = "当前解析对应的 Git revision")
        String currentRevision,
        @Schema(description = "源码结构契约版本")
        String currentStructureVersion,
        @Schema(description = "项目状态", allowableValues = {"CREATED", "INDEXING", "READY", "FAILED"})
        String status,
        @Schema(description = "创建时间")
        LocalDateTime createdAt,
        @Schema(description = "最后更新时间")
        LocalDateTime updatedAt,
        @Schema(description = "最近完成源码解析的时间")
        LocalDateTime lastIndexedAt
) {

    public static ProjectResponse from(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getSourceType(),
                project.getSourceLocation(),
                project.getDefaultBranch(),
                project.getCurrentRevision(),
                project.getCurrentStructureVersion(),
                project.getStatus(),
                project.getCreatedAt(),
                project.getUpdatedAt(),
                project.getLastIndexedAt()
        );
    }
}
