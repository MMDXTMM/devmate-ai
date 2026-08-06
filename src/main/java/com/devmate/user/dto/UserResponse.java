package com.devmate.user.dto;

import com.devmate.user.entity.AppUser;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record UserResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String username,
        String email
) {
    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail());
    }
}
