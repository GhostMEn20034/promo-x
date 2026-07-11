package com.promox.core.promotion.internal.exception;

public class InvalidConditionNodeException extends RuntimeException {

    private final String errorCode;

    public InvalidConditionNodeException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
