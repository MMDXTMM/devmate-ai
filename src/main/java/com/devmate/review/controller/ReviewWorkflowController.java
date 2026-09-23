package com.devmate.review.controller;

import com.devmate.common.api.ApiResponse;
import com.devmate.review.dto.CreateReviewWorkflowRequest;
import com.devmate.review.dto.ReviewWorkflowResponse;
import com.devmate.review.service.ReviewWorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/review-workflows")
@Validated
@Tag(name = "一键代码审查", description = "按固定阶段编排源码、Diff、静态分析、RAG 和 Agent 审查")
@SecurityRequirement(name = "bearerAuth")
public class ReviewWorkflowController {

    private final ReviewWorkflowService service;

    public ReviewWorkflowController(ReviewWorkflowService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(
            summary = "执行一键代码审查",
            description = "同步执行 SOURCE_IMPORT → DIFF → STATIC_ANALYSIS → EMBEDDING → AGENT_REVIEW。"
                    + "任一阶段失败即停止并返回 FAILED、失败阶段和恢复建议。"
                    + "相同 attemptKey 幂等返回原运行；同项目不同键并发执行返回 409。"
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "工作流已执行或幂等回读；业务结果见 data.status"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "项目 ID 或 attemptKey 格式不合法",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "未登录或令牌已过期",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "无权访问该项目",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "项目不存在",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "项目已有审查在运行，或 attemptKey 已被其他项目使用",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ApiResponse<ReviewWorkflowResponse> create(
            @Parameter(description = "项目 ID", required = true,
                    schema = @Schema(type = "string", pattern = "^[1-9][0-9]*$", example = "2084116785588305922"))
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId,
            @Valid @RequestBody CreateReviewWorkflowRequest request
    ) {
        return ApiResponse.success(service.create(projectId, request.attemptKey()));
    }

    @GetMapping("/latest")
    @Operation(
            summary = "查询最近一次代码审查",
            description = "用于页面重进、请求超时或刷新时恢复持久化状态；没有历史运行时返回 404。"
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "未登录或令牌已过期",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "无权访问该项目",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "项目不存在或还没有审查运行",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ApiResponse<ReviewWorkflowResponse> latest(
            @Parameter(description = "项目 ID", required = true,
                    schema = @Schema(type = "string", pattern = "^[1-9][0-9]*$", example = "2084116785588305922"))
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId
    ) {
        return ApiResponse.success(service.latest(projectId));
    }
}
