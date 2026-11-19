package com.devmate.generation.controller;
import com.devmate.common.api.ApiResponse;
import com.devmate.generation.dto.*;
import com.devmate.generation.service.*;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @Validated @RequestMapping("/api/generation-sessions/{sessionId}") @Tag(name="生成项目修改与版本")
public class GenerationChangeController {
    private final GenerationChangeService changes;private final GenerationRevisionService revisions;
    public GenerationChangeController(GenerationChangeService changes,GenerationRevisionService revisions){this.changes=changes;this.revisions=revisions;}
    @GetMapping("/runs/{runId}/revisions") @Operation(summary="列出最近50个源码版本")
    public ApiResponse<List<RevisionResponse>> list(@Positive @PathVariable Long sessionId,@Positive @PathVariable Long runId){return ApiResponse.success(revisions.list(sessionId,runId));}
    @GetMapping("/runs/{runId}/selection") @Operation(summary="读取当前选用版本")
    public ApiResponse<RevisionResponse> selected(@Positive @PathVariable Long sessionId,@Positive @PathVariable Long runId){return ApiResponse.success(changes.selected(sessionId,runId));}
    @PostMapping("/runs/{runId}/selection/{revisionId}") @Operation(summary="选用或恢复一个成功版本",description="只更新选用指针，不修改或删除任何源码")
    public ApiResponse<RevisionResponse> select(@Positive @PathVariable Long sessionId,@Positive @PathVariable Long runId,@Positive @PathVariable Long revisionId){return ApiResponse.success(changes.select(sessionId,runId,revisionId));}
    @PostMapping("/revisions/{revisionId}/change-plans") @Operation(summary="后台整理修改计划",description="显式使用模型额度，不修改源码；同attemptKey复用")
    public ResponseEntity<ApiResponse<ChangePlanResponse>> plan(@Positive @PathVariable Long sessionId,@Positive @PathVariable Long revisionId,@Valid @RequestBody ChangeProjectRequest request){return ResponseEntity.accepted().body(ApiResponse.success(changes.plan(sessionId,revisionId,request)));}
    @GetMapping("/revisions/{revisionId}/change-plans/latest") @Operation(summary="恢复最近修改计划，不调用模型")
    public ApiResponse<ChangePlanResponse> latest(@Positive @PathVariable Long sessionId,@Positive @PathVariable Long revisionId){return ApiResponse.success(changes.latest(sessionId,revisionId));}
    @GetMapping("/change-plans/{planId}") @Operation(summary="读取修改计划与生成状态")
    public ApiResponse<ChangePlanResponse> plan(@Positive @PathVariable Long sessionId,@Positive @PathVariable Long planId){return ApiResponse.success(changes.get(sessionId,planId));}
    @PostMapping("/change-plans/{planId}/confirmations") @Operation(summary="确认计划并后台生成独立新版本",description="幂等，使用模型额度，旧版本保留")
    public ResponseEntity<ApiResponse<ChangePlanResponse>> approve(@Positive @PathVariable Long sessionId,@Positive @PathVariable Long planId){return ResponseEntity.accepted().body(ApiResponse.success(changes.approve(sessionId,planId)));}
    @GetMapping("/revisions/{revisionId}/comparison") @Operation(summary="比较同项目两个成功版本的变更文件")
    public ApiResponse<RevisionComparison> compare(@Positive @PathVariable Long sessionId,@Positive @PathVariable Long revisionId,@Positive @RequestParam Long beforeId){return ApiResponse.success(revisions.compare(sessionId,beforeId,revisionId));}
    @GetMapping("/revisions/{revisionId}/diff") @Operation(summary="读取变更文件前后文本")
    public ApiResponse<RevisionComparison.FileDiff> diff(@Positive @PathVariable Long sessionId,@Positive @PathVariable Long revisionId,@Positive @RequestParam Long beforeId,@Size(min=1,max=240) @RequestParam String path){return ApiResponse.success(revisions.diff(sessionId,beforeId,revisionId,path));}
}
