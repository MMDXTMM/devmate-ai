package com.devmate.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "账号登录")
public record LoginRequest(
        @Schema(description = "用户名", example = "devmate_user")
        @NotBlank(message = "用户名不能为空") String username,
        @Schema(description = "登录密码", example = "Password123", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank(message = "密码不能为空") String password
) {
}
