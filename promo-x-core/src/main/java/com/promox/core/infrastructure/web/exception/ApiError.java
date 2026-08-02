package com.promox.core.infrastructure.web.exception;

import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

public record ApiError(
        ErrorCategory category,
        ErrorSource source,
        String path,
        String code,
        String message
) {}
