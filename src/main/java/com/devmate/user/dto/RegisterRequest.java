package com.devmate.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "用户名不能为空")
        @Pattern(regexp = "[A-Za-z0-9_]{3,32}", message = "用户名只能包含字母、数字和下划线，长度为3到32位")
        String username,
        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 72, message = "密码长度必须为8到72位")
        String password,
        @Email(message = "邮箱格式不正确")
        @Size(max = 255, message = "邮箱长度不能超过255位")
        String email
) {
}
