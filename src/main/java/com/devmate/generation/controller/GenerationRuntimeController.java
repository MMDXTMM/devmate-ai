package com.devmate.generation.controller;
import com.devmate.common.api.ApiResponse;
import com.devmate.generation.dto.*;
import com.devmate.generation.service.*;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
@RestController @Validated @RequestMapping("/api/generation-sessions/{sessionId}/revisions/{revisionId}") @Tag(name="生成项目运行向导")
public class GenerationRuntimeController {
 private final GenerationRuntimeGuideService runtime;private final GenerationInsightService insights;
 public GenerationRuntimeController(GenerationRuntimeGuideService runtime,GenerationInsightService insights){this.runtime=runtime;this.insights=insights;}
 @GetMapping("/runtime-guide") @Operation(summary="根据实际文件获取运行步骤",description="确定性文件识别，无模型调用，不执行命令，不检测用户电脑")
 public ApiResponse<RuntimeGuideResponse> guide(@Positive @PathVariable Long sessionId,@Positive @PathVariable Long revisionId){return ApiResponse.success(runtime.guide(sessionId,revisionId));}
 @PostMapping("/error-help") @Operation(summary="解释当前版本的运行报错",description="常见凭据先脱敏，显式使用模型额度，只给建议，不执行命令")
 public ResponseEntity<ApiResponse<InsightResponse>> error(@Positive @PathVariable Long sessionId,@Positive @PathVariable Long revisionId,@Valid @RequestBody ErrorHelpRequest request){return ResponseEntity.accepted().body(ApiResponse.success(insights.errorHelp(sessionId,revisionId,request)));}
 @GetMapping("/error-help/latest") @Operation(summary="读取保存的报错解释，不调用模型")
 public ApiResponse<InsightResponse> latest(@Positive @PathVariable Long sessionId,@Positive @PathVariable Long revisionId){return ApiResponse.success(insights.latest(sessionId,revisionId,"ERROR_HELP"));}
}
