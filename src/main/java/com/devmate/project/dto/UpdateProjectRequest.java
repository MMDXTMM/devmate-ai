package com.devmate.project.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "完整更新项目可编辑元数据；状态和索引版本由服务端维护")
public record UpdateProjectRequest(
        @Schema(description = "项目名称", example = "devmate-ai", maxLength = 100)
        @NotBlank(message = "项目名称不能为空")
        @Size(max = 100, message = "项目名称不能超过100个字符")
        String name,

        @Schema(description = "项目用途说明", example = "Java 项目理解与代码审查 Agent", maxLength = 500)
        @Size(max = 500, message = "项目描述不能超过500个字符")
        String description,

        @Schema(description = "源码来源", example = "GIT", requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"LOCAL", "GIT", "UPLOAD"})
        @NotBlank(message = "源码类型不能为空")
        @Pattern(regexp = "LOCAL|GIT|UPLOAD", message = "源码类型只能是LOCAL、GIT或UPLOAD")
        String sourceType,

        @Schema(description = "Git HTTPS 地址、本地路径或上传位置；GIT 类型必填",
                example = "https://github.com/example/devmate-ai.git", maxLength = 1000)
        @Size(max = 1000, message = "源码位置不能超过1000个字符")
        String sourceLocation,

        @Schema(description = "默认分支", example = "main", maxLength = 100)
        @Size(max = 100, message = "默认分支不能超过100个字符")
        String defaultBranch
) {
}
