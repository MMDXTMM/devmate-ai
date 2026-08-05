package com.devmate.review.dto;

import com.devmate.agent.model.AiFindingCategory;
import com.devmate.review.model.ReviewExpectationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateReviewEvaluationCaseRequest(
        @NotNull(message = "Diff任务ID不能为空")
        @Positive(message = "Diff任务ID必须大于0")
        Long reviewTaskId,

        @NotBlank(message = "评测集版本不能为空")
        @Size(max = 64, message = "评测集版本不能超过64个字符")
        @Pattern(regexp = "[A-Za-z0-9._-]+", message = "评测集版本只能包含字母、数字、点、下划线和短横线")
        String datasetVersion,

        @NotBlank(message = "用例键不能为空")
        @Size(max = 100, message = "用例键不能超过100个字符")
        @Pattern(regexp = "[A-Za-z0-9._-]+", message = "用例键只能包含字母、数字、点、下划线和短横线")
        String caseKey,

        @NotBlank(message = "用例名称不能为空")
        @Size(max = 200, message = "用例名称不能超过200个字符")
        String name,

        @NotNull(message = "期望类型不能为空")
        ReviewExpectationType expectationType,

        AiFindingCategory category,

        @Size(max = 1000, message = "文件路径不能超过1000个字符")
        String filePath,

        @Positive(message = "起始行必须大于0")
        Integer startLine,

        @Positive(message = "结束行必须大于0")
        Integer endLine,

        @NotBlank(message = "标注依据不能为空")
        @Size(max = 1000, message = "标注依据不能超过1000个字符")
        String rationale
) {
}
