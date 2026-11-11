package com.devmate.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "保存当前账户的模型连接")
public record ModelConnectionUpdateRequest(
        @Schema(description = "模型提供方；COMPATIBLE 为通用 OpenAI 兼容网关或中转站，需服务端配置启用", example = "DEEPSEEK",
                allowableValues = {"DEEPSEEK", "DASHSCOPE", "OPENAI", "COMPATIBLE"})
        @NotBlank(message = "模型提供方不能为空")
        @Pattern(regexp = "DEEPSEEK|DASHSCOPE|OPENAI|COMPATIBLE", message = "暂不支持该模型提供方")
        String provider,
        @Schema(description = "提供方实际支持的模型名称", example = "deepseek-chat")
        @NotBlank(message = "模型名称不能为空")
        @Size(max = 100, message = "模型名称过长")
        String model,
        @Schema(description = "模型 API Key；只允许写入，不会通过任何响应回显", writeOnly = true)
        @Size(max = 500, message = "API Key过长")
        String apiKey
) {
}
