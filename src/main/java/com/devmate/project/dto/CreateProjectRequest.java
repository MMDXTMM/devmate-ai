package com.devmate.project.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "创建待理解或审查的 Java 项目")
public record CreateProjectRequest(
        @Schema(description = "项目名称", example = "demo-service", maxLength = 100)
        @NotBlank(message = "项目名称不能为空")
        @Size(max = 100, message = "项目名称不能超过100个字符")
        String name,

        @Schema(description = "项目用途说明", example = "用于演示代码审查的 Spring Boot 项目", maxLength = 500)
        @Size(max = 500, message = "项目描述不能超过500个字符")
        String description,

        @Schema(description = "源码来源；不传时按 LOCAL 处理", example = "GIT",
                allowableValues = {"LOCAL", "GIT", "UPLOAD"})
        @Pattern(regexp = "LOCAL|GIT|UPLOAD", message = "源码类型只能是LOCAL、GIT或UPLOAD")
        String sourceType,

        @Schema(description = "Git HTTPS 地址、本地路径或上传位置；GIT 类型必填",
                example = "https://github.com/example/demo-service.git", maxLength = 1000)
        @Size(max = 1000, message = "源码位置不能超过1000个字符")
        String sourceLocation,

        @Schema(description = "默认分支", example = "main", maxLength = 100)
        @Size(max = 100, message = "默认分支不能超过100个字符")
        String defaultBranch
) {
}
