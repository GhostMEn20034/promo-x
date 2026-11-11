package com.promox.core.infrastructure.exception;

/**
 * A value in the request is invalid in a way only the object being built from it can tell, such as an operator a
 * condition doesn't support. Thrown while the request body is read, it is reported as a validation error with
 * {@link #getErrorCode()} and the exception's message, at the path of the object that rejected the value.
 */
public abstract class InvalidValueException extends RuntimeException {

    private final String errorCode;

    protected InvalidValueException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
