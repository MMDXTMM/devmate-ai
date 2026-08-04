package com.devmate.agent.model;

public class AiReviewException extends RuntimeException {

    public AiReviewException(String message) {
        super(message);
    }

    public AiReviewException(String message, Throwable cause) {
        super(message, cause);
    }
}
