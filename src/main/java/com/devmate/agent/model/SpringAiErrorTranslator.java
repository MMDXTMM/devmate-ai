package com.devmate.agent.model;

import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

final class SpringAiErrorTranslator {

    private SpringAiErrorTranslator() { }

    static AiReviewException reviewFailure(String operation, RuntimeException exception) {
        Integer status = responseStatus(exception);
        if (Integer.valueOf(401).equals(status) || Integer.valueOf(403).equals(status)) {
            return new AiReviewException(operation + "失败：API Key无效或没有模型权限", exception);
        }
        if (Integer.valueOf(429).equals(status)) {
            return new AiReviewException(operation + "请求过于频繁或额度不足，请稍后重试并检查额度", exception);
        }
        if (hasCause(exception, ResourceAccessException.class)
                || hasCause(exception, ConnectException.class)
                || hasCause(exception, SocketTimeoutException.class)) {
            return new AiReviewException(operation + "失败：无法连接模型服务", exception);
        }
        return new AiReviewException(operation + "失败", exception);
    }

    private static Integer responseStatus(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof RestClientResponseException responseException) {
                return responseException.getStatusCode().value();
            }
            if (current instanceof NonTransientAiException && current.getMessage() != null) {
                String message = current.getMessage().trim();
                if (message.matches("^[1-5][0-9]{2}\\s*-.*")) {
                    return Integer.parseInt(message.substring(0, 3));
                }
            }
            current = current.getCause();
        }
        return null;
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) return true;
            current = current.getCause();
        }
        return false;
    }
}
