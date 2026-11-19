package com.devmate.generation.controller;

import com.devmate.common.api.ApiResponse;
import com.devmate.generation.dto.ConfirmGenerationSpecRequest;
import com.devmate.generation.dto.CreateGenerationSessionRequest;
import com.devmate.generation.dto.GenerationSessionResponse;
import com.devmate.generation.dto.SubmitClarificationRequest;
import com.devmate.generation.service.GenerationSessionService;
import com.devmate.generation.service.ProjectGenerationService;
import com.devmate.generation.dto.GenerateProjectRequest;
import com.devmate.generation.dto.GenerationRunResponse;
import com.devmate.generation.dto.GeneratedFileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.validation.constraints.Size;
import java.util.List;

@RestController
@RequestMapping("/api/generation-sessions")
@Validated
@Tag(name = "多语言项目生成", description = "账户隔离的AI选择题追问、版本化方案确认、后台生成与源码下载")
public class GenerationSessionController {

    private final GenerationSessionService generationSessionService;
    private final ProjectGenerationService projectGenerationService;

    public GenerationSessionController(GenerationSessionService generationSessionService, ProjectGenerationService projectGenerationService) {
        this.generationSessionService = generationSessionService;
        this.projectGenerationService = projectGenerationService;
    }

    @GetMapping
    @Operation(summary = "列出当前账户最近20个生成会话", description = "只读取历史，不调用模型。按更新时间和ID倒序。")
    public ApiResponse<List<GenerationSessionResponse>> listSessions() {
        return ApiResponse.success(generationSessionService.listSessions());
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

    @PostMapping("/{sessionId}/runs")
    @Operation(summary = "生成已确认方案的源码包", description = "202后台任务；确认版本与attemptKey必填。同版本成功或进行中复用；失败用新attemptKey显式重试。模型与文件操作不在数据库事务中。")
    public ResponseEntity<ApiResponse<GenerationRunResponse>> generate(
            @Positive @PathVariable Long sessionId, @Valid @RequestBody GenerateProjectRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(projectGenerationService.start(sessionId, request)));
    }

    @GetMapping("/{sessionId}/runs/latest")
    @Operation(summary = "读取最近生成任务", description = "未生成时data为null；不调用模型，超时任务记录为失败。")
    public ApiResponse<GenerationRunResponse> latest(@Positive @PathVariable Long sessionId) {
        return ApiResponse.success(projectGenerationService.latest(sessionId));
    }

    @GetMapping("/{sessionId}/runs/{runId}/files")
    @Operation(summary = "读取已生成文件的文本", description = "仅当前账户的成功任务且path属于文件清单；错误项目或文件404，未完成409。")
    public ApiResponse<GeneratedFileResponse> file(@Positive @PathVariable Long sessionId, @Positive @PathVariable Long runId,
                                                 @Size(min = 1, max = 240) @RequestParam String path) {
        return ApiResponse.success(projectGenerationService.file(sessionId, runId, path));
    }

    @GetMapping(value = "/{sessionId}/runs/{runId}/download", produces = "application/zip")
    @Operation(summary = "下载生成项目ZIP", description = "需要当前账户认证；不执行生成源码。失败返回统一错误响应。")
    public ResponseEntity<byte[]> download(@Positive @PathVariable Long sessionId, @Positive @PathVariable Long runId) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=project-" + runId + ".zip")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(projectGenerationService.download(sessionId, runId));
    }
}
