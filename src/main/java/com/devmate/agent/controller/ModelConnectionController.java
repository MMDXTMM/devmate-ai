package com.devmate.agent.controller;

import com.devmate.agent.dto.ModelConnectionTestResponse;
import com.devmate.agent.dto.ModelConnectionUpdateRequest;
import com.devmate.agent.dto.ModelProviderResponse;
import com.devmate.agent.service.ModelConnectionService;
import com.devmate.common.api.ApiResponse;
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
public class ModelConnectionController {
    private final ModelConnectionService service;

    public ModelConnectionController(ModelConnectionService service) { this.service = service; }

    @GetMapping
    public ApiResponse<List<ModelProviderResponse>> list() { return ApiResponse.success(service.list()); }

    @PutMapping
    public ApiResponse<List<ModelProviderResponse>> update(
            @Valid @RequestBody ModelConnectionUpdateRequest request
    ) { return ApiResponse.success(service.update(request)); }

    @PostMapping("/test")
    public ApiResponse<ModelConnectionTestResponse> test() { return ApiResponse.success(service.test()); }
}
