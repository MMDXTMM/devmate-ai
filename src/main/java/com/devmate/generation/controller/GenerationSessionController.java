package com.devmate.generation.controller;

import com.devmate.common.api.ApiResponse;
import com.devmate.generation.dto.ConfirmGenerationSpecRequest;
import com.devmate.generation.dto.CreateGenerationSessionRequest;
import com.devmate.generation.dto.GenerationSessionResponse;
import com.devmate.generation.dto.SubmitClarificationRequest;
import com.devmate.generation.service.GenerationSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "需求生成会话（冻结）", description = "保留的一句话项目生成需求澄清契约；当前不继续扩展代码生成能力")
public class GenerationSessionController {

    private final GenerationSessionService generationSessionService;

    public GenerationSessionController(GenerationSessionService generationSessionService) {
        this.generationSessionService = generationSessionService;
    }

    @PostMapping
    @Operation(summary = "创建需求澄清会话")
    public ResponseEntity<ApiResponse<GenerationSessionResponse>> createSession(
            @Valid @RequestBody CreateGenerationSessionRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(generationSessionService.createSession(request)));
    }

    @GetMapping("/{sessionId}")
    @Operation(summary = "查询需求澄清会话")
    public ApiResponse<GenerationSessionResponse> getSession(
            @Positive(message = "生成会话ID必须大于0") @PathVariable Long sessionId
    ) {
        return ApiResponse.success(generationSessionService.getSession(sessionId));
    }

    @PostMapping("/{sessionId}/clarifications")
    @Operation(summary = "提交需求澄清答案", description = "成功后创建新的不可变方案版本。")
    public ApiResponse<GenerationSessionResponse> submitClarification(
            @Positive(message = "生成会话ID必须大于0") @PathVariable Long sessionId,
            @Valid @RequestBody SubmitClarificationRequest request
    ) {
        return ApiResponse.success(generationSessionService.submitClarification(sessionId, request));
    }

    @PostMapping("/{sessionId}/confirmations")
    @Operation(summary = "确认最新需求方案", description = "确认后会话锁定，不能继续修改。")
    public ApiResponse<GenerationSessionResponse> confirmSpec(
            @Positive(message = "生成会话ID必须大于0") @PathVariable Long sessionId,
            @Valid @RequestBody ConfirmGenerationSpecRequest request
    ) {
        return ApiResponse.success(generationSessionService.confirmSpec(sessionId, request));
    }
}
