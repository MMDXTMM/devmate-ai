package com.devmate.generation.controller;
import com.devmate.common.api.ApiResponse;
import com.devmate.generation.dto.*;
import com.devmate.generation.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
@RestController @Validated @RequestMapping("/api/generation-sessions/{sessionId}")
@Tag(name="生成项目增强",description="版本化源码、受控隔离验证与有限修复，全部校验会话归属")
public class GenerationRevisionController {
    private final GenerationRevisionService revisions;
    private final GenerationVerificationService checks;
    public GenerationRevisionController(GenerationRevisionService revisions,GenerationVerificationService checks) { this.revisions=revisions;this.checks=checks; }
    @PostMapping("/runs/{runId}/revisions") @Operation(summary="为已生成项目创建或读取原始版本快照",description="幂等，不调用AI、不执行代码")
    public ApiResponse<RevisionResponse> root(@Positive @PathVariable Long sessionId,@Positive @PathVariable Long runId) { return ApiResponse.success(revisions.root(sessionId,runId)); }
    @GetMapping("/revisions/{revisionId}") @Operation(summary="读取源码版本")
    public ApiResponse<RevisionResponse> get(@Positive @PathVariable Long sessionId,@Positive @PathVariable Long revisionId) { return ApiResponse.success(revisions.get(sessionId,revisionId)); }
    @GetMapping("/revisions/{revisionId}/files") @Operation(summary="读取版本内白名单文件")
    public ApiResponse<GeneratedFileResponse> file(@Positive @PathVariable Long sessionId,@Positive @PathVariable Long revisionId,@Size(min=1,max=240) @RequestParam String path) { return ApiResponse.success(revisions.file(sessionId,revisionId,path)); }
    @GetMapping("/revisions/{revisionId}/download") @Operation(summary="下载版本ZIP")
    public ResponseEntity<byte[]> download(@Positive @PathVariable Long sessionId,@Positive @PathVariable Long revisionId) { return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip")).header(HttpHeaders.CACHE_CONTROL,"no-store").header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=revision-"+revisionId+".zip").body(revisions.download(sessionId,revisionId)); }
    @PostMapping("/revisions/{revisionId}/verifications") @Operation(summary="后台验证源码并可选最多2次修复",description="固定离线容器与命令；repairLimit=0不调用模型。不可运行时BLOCKED，绝不冒充通过")
    public ResponseEntity<ApiResponse<VerificationResponse>> verify(@Positive @PathVariable Long sessionId,@Positive @PathVariable Long revisionId,@Valid @RequestBody VerificationRequest request) { return ResponseEntity.accepted().body(ApiResponse.success(checks.start(sessionId,revisionId,request))); }
    @GetMapping("/revisions/{revisionId}/verifications/latest") @Operation(summary="读取最新验证与真实退出码，不自动运行")
    public ApiResponse<VerificationResponse> latest(@Positive @PathVariable Long sessionId,@Positive @PathVariable Long revisionId) { return ApiResponse.success(checks.latest(sessionId,revisionId)); }
}
