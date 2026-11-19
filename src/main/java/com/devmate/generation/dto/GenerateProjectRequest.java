package com.devmate.generation.dto;

import jakarta.validation.constraints.*;

public record GenerateProjectRequest(
        @io.swagger.v3.oas.annotations.media.Schema(type = "string", description = "已确认的方案版本ID")
        @NotNull @Positive Long versionId,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{16,80}", message = "生成请求标识不合法") String attemptKey
) { }
