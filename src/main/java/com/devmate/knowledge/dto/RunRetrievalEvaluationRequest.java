package com.devmate.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RunRetrievalEvaluationRequest(
        @NotBlank(message = "评测集版本不能为空")
        @Size(max = 64, message = "评测集版本不能超过64个字符")
        String datasetVersion
) {
}
