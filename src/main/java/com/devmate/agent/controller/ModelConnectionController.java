package com.devmate.agent.controller;

import com.devmate.agent.dto.ModelConnectionTestResponse;
import com.devmate.agent.dto.ModelConnectionUpdateRequest;
import com.devmate.agent.dto.ModelProviderResponse;
import com.devmate.agent.service.ModelConnectionService;
import com.devmate.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/model-connections")
@Tag(name = "模型连接", description = "配置当前账户使用的模型提供方，并执行不泄露密钥的连接测试")
public class ModelConnectionController {
    private final ModelConnectionService service;

    public ModelConnectionController(ModelConnectionService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "查询可用模型提供方和当前配置")
    public ApiResponse<List<ModelProviderResponse>> list() { return ApiResponse.success(service.list()); }

    @PutMapping
    @Operation(summary = "保存当前账户的模型连接", description = "API Key 只写入服务端加密存储，不在响应中返回。")
    public ApiResponse<List<ModelProviderResponse>> update(
            @Valid @RequestBody ModelConnectionUpdateRequest request
    ) { return ApiResponse.success(service.update(request)); }

    @PostMapping("/test")
    @Operation(summary = "测试当前模型连接", description = "发起一次受超时限制的最小模型请求，并返回脱敏诊断。")
    public ApiResponse<ModelConnectionTestResponse> test() { return ApiResponse.success(service.test()); }
}
