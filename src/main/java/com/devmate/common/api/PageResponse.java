package com.devmate.common.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "统一分页结果，页码从 1 开始")
public record PageResponse<T>(
        @Schema(description = "当前页码", example = "1", minimum = "1")
        long page,
        @Schema(description = "每页数量", example = "20", minimum = "1", maximum = "100")
        long size,
        @Schema(description = "符合条件的总记录数", example = "3")
        long total,
        @Schema(description = "总页数", example = "1")
        long pages,
        @Schema(description = "当前页数据")
        List<T> items
) {
}
