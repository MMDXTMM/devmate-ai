package com.devmate.conversation.controller;

import com.devmate.common.api.ApiResponse;
import com.devmate.conversation.dto.ConversationMessageResponse;
import com.devmate.conversation.dto.ConversationResponse;
import com.devmate.conversation.dto.CreateConversationMessageRequest;
import com.devmate.conversation.dto.CreateConversationRequest;
import com.devmate.conversation.service.ProjectConversationService;
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
public class ProjectConversationController {
    private final ProjectConversationService service;

    public ProjectConversationController(ProjectConversationService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ConversationResponse> create(
            @Positive @PathVariable Long projectId,
            @Valid @RequestBody(required = false) CreateConversationRequest request
    ) {
        return ApiResponse.success(service.create(projectId, request));
    }

    @GetMapping
    public ApiResponse<List<ConversationResponse>> list(@Positive @PathVariable Long projectId) {
        return ApiResponse.success(service.list(projectId));
    }

    @GetMapping("/{conversationId}/messages")
    public ApiResponse<List<ConversationMessageResponse>> messages(
            @Positive @PathVariable Long projectId,
            @Positive @PathVariable Long conversationId
    ) {
        return ApiResponse.success(service.messages(projectId, conversationId));
    }

    @PostMapping(value = "/{conversationId}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @Positive @PathVariable Long projectId,
            @Positive @PathVariable Long conversationId,
            @Valid @RequestBody CreateConversationMessageRequest request
    ) {
        return service.stream(projectId, conversationId, request);
    }
}

