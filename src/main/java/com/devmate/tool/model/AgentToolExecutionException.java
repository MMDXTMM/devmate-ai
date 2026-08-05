package com.devmate.tool.model;

public class AgentToolExecutionException extends RuntimeException {

    public AgentToolExecutionException(String message) {
        super(message);
    }

    public AgentToolExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
