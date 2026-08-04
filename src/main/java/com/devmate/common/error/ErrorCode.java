package com.devmate.common.error;

public enum ErrorCode {

    INVALID_ARGUMENT(40000, "请求参数不合法"),
    RESOURCE_NOT_FOUND(40400, "资源不存在"),
    CONFLICT(40900, "资源状态冲突"),
    INTERNAL_ERROR(50000, "系统内部错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
