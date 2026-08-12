package com.promox.core.infrastructure.exception;


import jakarta.annotation.Nullable;

import java.util.Map;

public record BusinessError(
        @Nullable String path,
        String code,
        String message,
        @Nullable Map<String, Object> errorContext
) {}
