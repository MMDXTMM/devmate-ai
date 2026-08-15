package com.devmate.generation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateGenerationSessionRequest(
        @NotBlank(message = "请用一句话描述你想创建的项目")
        @Size(max = 2000, message = "需求描述不能超过2000个字符")
        String requirement
) {
}
