package com.devmate.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProjectRequest(
        @NotBlank(message = "项目名称不能为空")
        @Size(max = 100, message = "项目名称不能超过100个字符")
        String name,

        @Size(max = 500, message = "项目描述不能超过500个字符")
        String description,

        @NotBlank(message = "源码类型不能为空")
        @Pattern(regexp = "LOCAL|GIT|UPLOAD", message = "源码类型只能是LOCAL、GIT或UPLOAD")
        String sourceType,

        @Size(max = 1000, message = "源码位置不能超过1000个字符")
        String sourceLocation,

        @Size(max = 100, message = "默认分支不能超过100个字符")
        String defaultBranch
) {
}
