package com.promox.core.infrastructure.exception;

import java.util.List;


public abstract class BusinessException extends RuntimeException {

    private final String code;
    private final List<BusinessError> errors;

    protected BusinessException(
            String message,
            String code,
            List<BusinessError> errors
    ) {
        super(message);
        this.code = code;
        this.errors = List.copyOf(errors);
    }

    public String getCode() {
        return code;
    }

    public List<BusinessError> getErrors() {
        return errors;
    }
}
