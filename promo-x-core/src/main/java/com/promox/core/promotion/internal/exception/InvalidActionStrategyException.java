package com.promox.core.promotion.internal.exception;

public class InvalidActionStrategyException extends RuntimeException {

    private final String errorCode;

    public InvalidActionStrategyException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

}
