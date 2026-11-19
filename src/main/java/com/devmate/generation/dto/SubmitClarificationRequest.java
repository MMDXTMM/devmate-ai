package com.devmate.generation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SubmitClarificationRequest(
        @NotEmpty(message = "请至少回答一个澄清问题")
        @Size(max = 10, message = "一次最多提交10个答案")
        List<@Valid RequirementAnswerRequest> answers,
        @jakarta.validation.constraints.Positive
        @io.swagger.v3.oas.annotations.media.Schema(type = "string", description = "当前版本ID，新客户端提交以拒绝过期答案")
        Long versionId
) {
}
