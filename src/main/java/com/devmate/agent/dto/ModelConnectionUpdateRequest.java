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
        String apiKey,
        @Schema(description = "COMPATIBLE 的 HTTPS 基础地址，可粘贴完整操作地址自动规范化；必须属于服务端受信任地址名单，省略保持原地址")
        @Size(max = 500, message = "API 请求地址过长") String baseUrl,
        @Schema(description = "接口协议；目前只支持 CHAT_COMPLETIONS，不能把 Responses 当作相同协议", allowableValues = {"CHAT_COMPLETIONS"})
        @Pattern(regexp = "CHAT_COMPLETIONS", message = "当前仅支持 Chat Completions 协议，请确认中转站支持该接口") String protocol
) {
    public ModelConnectionUpdateRequest(String provider, String model, String apiKey) {
        this(provider, model, apiKey, null, null);
    }
}
