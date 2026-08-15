package com.devmate.generation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ConfirmGenerationSpecRequest(
        @NotNull(message = "方案版本ID不能为空")
        @Positive(message = "方案版本ID必须大于0")
        Long versionId
) {
}
