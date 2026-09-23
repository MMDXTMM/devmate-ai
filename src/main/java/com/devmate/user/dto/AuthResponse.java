package com.devmate.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "登录凭证与用户信息")
public record AuthResponse(
        @Schema(description = "JWT 访问令牌", accessMode = Schema.AccessMode.READ_ONLY)
        String accessToken,
        @Schema(description = "Authorization 头使用的令牌类型", example = "Bearer")
        String tokenType,
        @Schema(description = "令牌失效时间")
        Instant expiresAt,
        @Schema(description = "当前用户")
        UserResponse user
) {
}
