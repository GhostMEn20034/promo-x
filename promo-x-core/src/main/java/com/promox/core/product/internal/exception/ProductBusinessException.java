package com.promox.core.product.internal.exception;

import com.promox.core.infrastructure.exception.BusinessError;
import com.promox.core.infrastructure.exception.BusinessException;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.Map;

public final class ProductBusinessException extends BusinessException {

    private ProductBusinessException(
            String message,
            String code,
            List<BusinessError> errors
    ) {
        super(message, code, errors);
    }

    public static ProductBusinessException of(
            String message,
            String code,
            @Nullable String path,
            String errorMessage,
            @Nullable Map<String, Object> errorContext
    ) {
        return new ProductBusinessException(
                message,
                code,
                List.of(
                        new BusinessError(
                                path,
                                code,
                                errorMessage,
                                errorContext
                        )
                )
        );
    }

    public static ProductBusinessException of(
        String message,
        String code,
        @Nullable String path,
        String errorMessage
    ) {
        return of(message, code, path, errorMessage, null);
    }

    public static ProductBusinessException of(
            String message,
            String code,
            List<BusinessError> errors
    ) {
        return new ProductBusinessException(
                message,
                code,
                errors
        );
    }
}
