package com.devmate.project.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProjectQueryRequest(
        @Min(value = 1, message = "页码必须大于0")
        Integer page,

        @Min(value = 1, message = "每页数量必须大于0")
        @Max(value = 100, message = "每页数量不能超过100")
        Integer size,

        @Size(max = 100, message = "项目名称不能超过100个字符")
        String name,

        @Pattern(regexp = "CREATED|INDEXING|READY|FAILED", message = "项目状态不合法")
        String status
) {

    public ProjectQueryRequest {
        page = page == null ? 1 : page;
        size = size == null ? 20 : size;
    }
}
