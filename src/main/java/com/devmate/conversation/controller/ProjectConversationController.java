package com.devmate.conversation.controller;

import com.devmate.common.api.ApiResponse;
import com.devmate.conversation.dto.ConversationMessageResponse;
import com.devmate.conversation.dto.ConversationResponse;
import com.devmate.conversation.dto.CreateConversationMessageRequest;
import com.devmate.conversation.dto.CreateConversationRequest;
import com.devmate.conversation.service.ProjectConversationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/agent-conversations")
@Validated
@Tag(name = "项目对话", description = "基于固定项目版本和代码证据进行持久化多轮问答")
public class ProjectConversationController {
    private final ProjectConversationService service;

    public ProjectConversationController(ProjectConversationService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "创建项目理解对话", description = "会话固定当前项目版本和模型配置。")
    public ApiResponse<ConversationResponse> create(
            @Positive @PathVariable Long projectId,
            @Valid @RequestBody(required = false) CreateConversationRequest request
    ) {
        return ApiResponse.success(service.create(projectId, request));
    }

    @GetMapping
    @Operation(summary = "查询项目对话列表")
    public ApiResponse<List<ConversationResponse>> list(@Positive @PathVariable Long projectId) {
        return ApiResponse.success(service.list(projectId));
    }

    @GetMapping("/{conversationId}/messages")
    @Operation(summary = "查询对话历史消息和代码证据")
    public ApiResponse<List<ConversationMessageResponse>> messages(
            @Positive @PathVariable Long projectId,
            @Positive @PathVariable Long conversationId
    ) {
        return ApiResponse.success(service.messages(projectId, conversationId));
    }

    @PostMapping(value = "/{conversationId}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            summary = "流式提问项目",
            description = "通过 SSE 返回 message、evidence、token、done 或 failed 事件；attemptKey 用于防止重复付费。"
    )
    public SseEmitter stream(
            @Positive @PathVariable Long projectId,
            @Positive @PathVariable Long conversationId,
            @Valid @RequestBody CreateConversationMessageRequest request
    ) {
        return service.stream(projectId, conversationId, request);
    }
}
