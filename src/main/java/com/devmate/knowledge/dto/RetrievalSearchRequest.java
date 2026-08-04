package com.devmate.knowledge.dto;

import com.devmate.knowledge.retrieval.RetrievalMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RetrievalSearchRequest(
        @NotBlank(message = "检索问题不能为空")
        @Size(max = 500, message = "检索问题不能超过500个字符")
        String query,
        @Pattern(regexp = "[0-9a-fA-F]{7,64}", message = "revision必须是7到64位十六进制提交ID")
        String revision,
        @Size(max = 20, message = "种子Chunk不能超过20个")
        List<@Positive(message = "种子Chunk ID必须大于0") Long> seedChunkIds,
        @Min(value = 1, message = "topK不能小于1")
        @Max(value = 20, message = "topK不能超过20")
        Integer topK,
        @Min(value = 100, message = "Token预算不能小于100")
        @Max(value = 12000, message = "Token预算不能超过12000")
        Integer tokenBudget,
        RetrievalMode retrievalMode
) {
}
