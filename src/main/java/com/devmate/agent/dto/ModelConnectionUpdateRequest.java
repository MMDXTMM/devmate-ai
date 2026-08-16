package com.devmate.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ModelConnectionUpdateRequest(
        @NotBlank(message = "模型提供方不能为空")
        @Pattern(regexp = "DEEPSEEK|DASHSCOPE|OPENAI", message = "暂不支持该模型提供方")
        String provider,
        @NotBlank(message = "模型名称不能为空")
        @Size(max = 100, message = "模型名称过长")
        String model,
        @Size(max = 500, message = "API Key过长")
        String apiKey
) {
}
