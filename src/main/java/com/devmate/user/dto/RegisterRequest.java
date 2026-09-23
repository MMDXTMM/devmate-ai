package com.devmate.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "注册账号")
public record RegisterRequest(
        @Schema(description = "用户名，只能包含字母、数字和下划线", example = "devmate_user",
                minLength = 3, maxLength = 32)
        @NotBlank(message = "用户名不能为空")
        @Pattern(regexp = "[A-Za-z0-9_]{3,32}", message = "用户名只能包含字母、数字和下划线，长度为3到32位")
        String username,
        @Schema(description = "登录密码", example = "Password123", minLength = 8, maxLength = 72,
                accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 72, message = "密码长度必须为8到72位")
        String password,
        @Schema(description = "可选邮箱", example = "devmate@example.com", maxLength = 255)
        @Email(message = "邮箱格式不正确")
        @Size(max = 255, message = "邮箱长度不能超过255位")
        String email
) {
}
