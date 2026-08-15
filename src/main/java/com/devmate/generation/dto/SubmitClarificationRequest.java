package com.devmate.generation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SubmitClarificationRequest(
        @NotEmpty(message = "请至少回答一个澄清问题")
        @Size(max = 10, message = "一次最多提交10个答案")
        List<@Valid RequirementAnswerRequest> answers
) {
}
