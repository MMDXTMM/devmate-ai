package com.devmate.knowledge.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRetrievalEvaluationCaseRequest(
        @NotBlank(message = "评测集版本不能为空")
        @Size(max = 64, message = "评测集版本不能超过64个字符")
        String datasetVersion,
        @NotBlank(message = "评测用例名称不能为空")
        @Size(max = 100, message = "评测用例名称不能超过100个字符")
        String name,
        @NotBlank(message = "评测问题不能为空")
        @Size(max = 500, message = "评测问题不能超过500个字符")
        String query,
        @NotBlank(message = "预期文件路径不能为空")
        @Size(max = 1000, message = "预期文件路径不能超过1000个字符")
        String expectedFilePath,
        @Size(max = 500, message = "预期符号不能超过500个字符")
        String expectedSymbolName,
        @Min(value = 1, message = "topK不能小于1")
        @Max(value = 20, message = "topK不能超过20")
        Integer topK
) {
}
