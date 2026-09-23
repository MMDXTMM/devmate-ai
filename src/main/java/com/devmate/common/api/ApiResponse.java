package com.devmate.common.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "统一 API 响应")
public record ApiResponse<T>(
        @Schema(description = "业务码，0 表示成功", example = "0")
        int code,
        @Schema(description = "面向用户的结果说明", example = "success")
        String message,
        @Schema(description = "业务数据；失败时为空")
        T data,
        @Schema(description = "服务端响应时间", example = "2026-09-23T10:30:00Z")
        Instant timestamp
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "success", data, Instant.now());
    }

    public static ApiResponse<Void> failure(int code, String message) {
        return new ApiResponse<>(code, message, null, Instant.now());
    }
}
