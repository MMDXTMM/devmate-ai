package com.devmate.agent.model;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.HttpStatus;
import java.net.SocketTimeoutException;
import java.net.ConnectException;
import static org.assertj.core.api.Assertions.assertThat;

class SpringAiErrorTranslatorTest {
    @Test
    void distinguishesNestedTimeoutFromConnectionFailureAndDoesNotExposeRawDetails() {
        var timeout = SpringAiErrorTranslator.reviewFailure("项目理解模型调用",
                new RuntimeException(new ResourceAccessException("private endpoint", new SocketTimeoutException("raw secret"))));
        assertThat(timeout.getMessage()).contains("超时", "等待上限").doesNotContain("无法连接", "private endpoint", "raw secret");
        var disconnected = SpringAiErrorTranslator.reviewFailure("项目理解模型调用",
                new ResourceAccessException("private endpoint", new ConnectException("raw secret")));
        assertThat(disconnected.getMessage()).contains("无法连接").doesNotContain("超时", "private endpoint", "raw secret");
    }

    @Test
    void preservesAuthenticationAndRateLimitErrors() {
        assertThat(SpringAiErrorTranslator.reviewFailure("模型调用",
                new HttpClientErrorException(HttpStatus.UNAUTHORIZED, "private detail")).getMessage())
                .contains("API Key无效").doesNotContain("private detail");
        assertThat(SpringAiErrorTranslator.reviewFailure("模型调用",
                new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS, "private detail")).getMessage())
                .contains("额度").doesNotContain("private detail");
    }
}
