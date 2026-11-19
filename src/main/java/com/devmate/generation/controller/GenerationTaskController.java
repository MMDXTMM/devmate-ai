package com.devmate.generation.controller;
import com.devmate.common.api.ApiResponse;
import com.devmate.generation.dto.GenerationTaskResponse;
import com.devmate.generation.service.GenerationTaskService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
@RestController @Validated @RequestMapping("/api/generation-sessions/{sessionId}/tasks") @Tag(name="生成任务进度与消耗")
public class GenerationTaskController{
 private final GenerationTaskService tasks;public GenerationTaskController(GenerationTaskService tasks){this.tasks=tasks;}
 @GetMapping("/latest") @Operation(summary="读取任务真实阶段、耗时和返回的Token用量",description="缺少模型用量时usageComplete=false且Token省略，不估算费用")
 public ApiResponse<GenerationTaskResponse> latest(@Positive @PathVariable Long sessionId,@Size(max=24) @RequestParam String kind,@Positive @RequestParam Long referenceId){return ApiResponse.success(tasks.latest(sessionId,kind,referenceId));}
 @PostMapping("/{taskId}/cancellations") @Operation(summary="取消排队或运行中的任务",description="中断工作线程、停止后续处理、禁止取消结果发布；上游已接收的模型调用仍可能计费。重复取消幂等")
 public ApiResponse<GenerationTaskResponse> cancel(@Positive @PathVariable Long sessionId,@Positive @PathVariable Long taskId){return ApiResponse.success(tasks.cancel(sessionId,taskId));}
}
