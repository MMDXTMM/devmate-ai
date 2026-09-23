package com.devmate.user.dto;

import com.devmate.user.entity.AppUser;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "用户公开信息")
public record UserResponse(
        @Schema(description = "用户 ID", type = "string", example = "2084116785588305922")
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @Schema(description = "用户名", example = "devmate_user")
        String username,
        @Schema(description = "邮箱", example = "devmate@example.com")
        String email
) {
    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail());
    }
}
