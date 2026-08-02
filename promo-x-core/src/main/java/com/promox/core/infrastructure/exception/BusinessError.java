package com.promox.core.infrastructure.exception;


import jakarta.annotation.Nullable;

public record BusinessError(
        @Nullable String path,
        String code,
        String message
) {}
