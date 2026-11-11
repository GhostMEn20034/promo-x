package com.promox.core.promotion.internal.exception;

import com.promox.core.infrastructure.exception.InvalidValueException;

public class InvalidConditionNodeException extends InvalidValueException {

    public InvalidConditionNodeException(String errorCode, String message) {
        super(errorCode, message);
    }
}
