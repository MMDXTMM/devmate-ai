package com.devmate.user.security;

import com.devmate.common.api.ApiResponse;
import com.devmate.common.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class ApiSecurityResponseWriter {

    private ApiSecurityResponseWriter() {
    }

    static void write(
            HttpServletResponse response,
            ObjectMapper objectMapper,
            int status,
            ErrorCode errorCode
    ) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                ApiResponse.failure(errorCode.getCode(), errorCode.getMessage())
        );
    }
}
