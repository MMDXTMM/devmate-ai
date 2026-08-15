package com.devmate.generation.controller;

import com.devmate.common.api.ApiResponse;
import com.devmate.generation.dto.ConfirmGenerationSpecRequest;
import com.devmate.generation.dto.CreateGenerationSessionRequest;
import com.devmate.generation.dto.GenerationSessionResponse;
import com.devmate.generation.dto.SubmitClarificationRequest;
import com.devmate.generation.service.GenerationSessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/generation-sessions")
@Validated
public class GenerationSessionController {

    private final GenerationSessionService generationSessionService;

    public GenerationSessionController(GenerationSessionService generationSessionService) {
        this.generationSessionService = generationSessionService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<GenerationSessionResponse>> createSession(
            @Valid @RequestBody CreateGenerationSessionRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(generationSessionService.createSession(request)));
    }

    @GetMapping("/{sessionId}")
    public ApiResponse<GenerationSessionResponse> getSession(
            @Positive(message = "生成会话ID必须大于0") @PathVariable Long sessionId
    ) {
        return ApiResponse.success(generationSessionService.getSession(sessionId));
    }

    @PostMapping("/{sessionId}/clarifications")
    public ApiResponse<GenerationSessionResponse> submitClarification(
            @Positive(message = "生成会话ID必须大于0") @PathVariable Long sessionId,
            @Valid @RequestBody SubmitClarificationRequest request
    ) {
        return ApiResponse.success(generationSessionService.submitClarification(sessionId, request));
    }

    @PostMapping("/{sessionId}/confirmations")
    public ApiResponse<GenerationSessionResponse> confirmSpec(
            @Positive(message = "生成会话ID必须大于0") @PathVariable Long sessionId,
            @Valid @RequestBody ConfirmGenerationSpecRequest request
    ) {
        return ApiResponse.success(generationSessionService.confirmSpec(sessionId, request));
    }
}
