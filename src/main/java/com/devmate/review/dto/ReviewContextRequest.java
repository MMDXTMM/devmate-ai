package com.devmate.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record ReviewContextRequest(
        @Size(max = 500, message = "检索问题不能超过500个字符")
        String query,
        @Min(value = 1, message = "topK不能小于1")
        @Max(value = 20, message = "topK不能超过20")
        Integer topK,
        @Min(value = 100, message = "Token预算不能小于100")
        @Max(value = 12000, message = "Token预算不能超过12000")
        Integer tokenBudget
) {
}
