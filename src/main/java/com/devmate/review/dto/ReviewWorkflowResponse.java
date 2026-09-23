package com.devmate.review.dto;

import com.devmate.knowledge.dto.EmbeddingIndexTaskResponse;
import com.devmate.knowledge.dto.IndexTaskResponse;
import com.devmate.review.model.ReviewWorkflowStage;
import com.devmate.review.model.ReviewWorkflowStatus;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "一键代码审查工作流状态和各阶段结果")
public record ReviewWorkflowResponse(
        @Schema(description = "工作流 ID", type = "string", example = "2084116785588313000")
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @Schema(description = "所属项目 ID", type = "string", example = "2084116785588305922")
        @JsonSerialize(using = ToStringSerializer.class) Long projectId,
        @Schema(description = "创建请求幂等键", example = "123e4567-e89b-42d3-a456-426614174000")
        String attemptKey,
        @Schema(description = "工作流生命周期状态")
        ReviewWorkflowStatus status,
        @Schema(description = "当前阶段；失败时表示失败阶段，成功时为 COMPLETED")
        ReviewWorkflowStage currentStage,
        @Schema(description = "源码导入任务 ID", type = "string")
        @JsonSerialize(using = ToStringSerializer.class) Long indexTaskId,
        @Schema(description = "Diff 任务 ID", type = "string")
        @JsonSerialize(using = ToStringSerializer.class) Long reviewTaskId,
        @Schema(description = "静态分析任务 ID", type = "string")
        @JsonSerialize(using = ToStringSerializer.class) Long staticAnalysisTaskId,
        @Schema(description = "向量索引任务 ID", type = "string")
        @JsonSerialize(using = ToStringSerializer.class) Long embeddingTaskId,
        @Schema(description = "Agent 审查任务 ID", type = "string")
        @JsonSerialize(using = ToStringSerializer.class) Long aiReviewTaskId,
        @Schema(description = "脱敏后的失败原因；仅 FAILED 时存在")
        String errorMessage,
        @Schema(description = "面向用户的恢复动作；仅 FAILED 时存在")
        String recoveryAction,
        @Schema(description = "工作流创建时间")
        LocalDateTime createdAt,
        @Schema(description = "开始时间")
        LocalDateTime startedAt,
        @Schema(description = "结束时间；RUNNING 时为空")
        LocalDateTime finishedAt,
        @Schema(description = "本次请求内完成的源码导入结果；状态回读时可能为空")
        IndexTaskResponse sourceImport,
        @Schema(description = "本次请求内完成的 Diff 结果；状态回读时可能为空")
        ReviewDiffResponse reviewDiff,
        @Schema(description = "本次请求内完成的静态分析结果；状态回读时可能为空")
        StaticAnalysisResponse staticAnalysis,
        @Schema(description = "本次请求内完成的向量索引结果；状态回读时可能为空")
        EmbeddingIndexTaskResponse embeddingIndex,
        @Schema(description = "本次请求内完成的 Agent 审查结果；状态回读时可能为空")
        AiReviewResponse aiReview
) {
}
