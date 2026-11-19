package com.devmate.conversation.dto;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "宿主记录的只读取证过程及覆盖限制，不是模型思维链")
public record ConversationContextResponse(List<Action> actions, List<String> warnings, boolean memoryUsed) {
    public record Action(String tool, String status, int evidenceCount) { }
}
