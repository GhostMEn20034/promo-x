package com.promox.core.promotion.internal.exception;

import com.promox.core.infrastructure.exception.InvalidValueException;

public class InvalidActionStrategyException extends InvalidValueException {

    public InvalidActionStrategyException(String errorCode, String message) {
        super(errorCode, message);
    }
}
