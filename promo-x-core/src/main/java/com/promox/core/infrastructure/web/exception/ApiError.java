package com.promox.core.infrastructure.web.exception;

import jakarta.annotation.Nullable;

import java.util.Map;

public record ApiError(
        ErrorCategory category,
        ErrorSource source,
        String path,
        String code,
        String message,
        @Nullable Map<String, Object> errorContext
) {}
