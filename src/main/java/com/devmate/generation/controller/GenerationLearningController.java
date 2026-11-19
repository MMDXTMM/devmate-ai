package com.devmate.generation.controller;
import com.devmate.common.api.ApiResponse;
import com.devmate.generation.dto.*;
import com.devmate.generation.service.GenerationInsightService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
@RestController @Validated @RequestMapping("/api/generation-sessions/{sessionId}/revisions/{revisionId}") @Tag(name="生成项目学习导览")
public class GenerationLearningController {
 private final GenerationInsightService insights;
 public GenerationLearningController(GenerationInsightService insights){this.insights=insights;}
 @PostMapping("/learning-guides") @Operation(summary="为准确源码版本生成新人导览",description="显式使用模型额度，异步、幂等，引用路径必须存在，不执行源码")
 public ResponseEntity<ApiResponse<InsightResponse>> generate(@Positive @PathVariable Long sessionId,@Positive @PathVariable Long revisionId,@Valid @RequestBody InsightRequest request){return ResponseEntity.accepted().body(ApiResponse.success(insights.guide(sessionId,revisionId,request)));}
 @GetMapping("/learning-guides/latest") @Operation(summary="读取此源码版本的已保存导览，不调用模型")
 public ApiResponse<InsightResponse> latest(@Positive @PathVariable Long sessionId,@Positive @PathVariable Long revisionId){return ApiResponse.success(insights.latest(sessionId,revisionId,"GUIDE"));}
}
