package com.devmate.project.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "项目分页筛选条件")
public record ProjectQueryRequest(
        @Schema(description = "页码，从 1 开始", example = "1", defaultValue = "1", minimum = "1")
        @Min(value = 1, message = "页码必须大于0")
        Integer page,

        @Schema(description = "每页数量", example = "20", defaultValue = "20", minimum = "1", maximum = "100")
        @Min(value = 1, message = "每页数量必须大于0")
        @Max(value = 100, message = "每页数量不能超过100")
        Integer size,

        @Schema(description = "按项目名称模糊匹配", example = "devmate", maxLength = 100)
        @Size(max = 100, message = "项目名称不能超过100个字符")
        String name,

        @Schema(description = "项目状态", example = "READY",
                allowableValues = {"CREATED", "INDEXING", "READY", "FAILED"})
        @Pattern(regexp = "CREATED|INDEXING|READY|FAILED", message = "项目状态不合法")
        String status
) {

    public ProjectQueryRequest {
        page = page == null ? 1 : page;
        size = size == null ? 20 : size;
    }
}
