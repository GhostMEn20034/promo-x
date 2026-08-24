package com.promox.core.promotion.internal.exception;

import com.promox.core.infrastructure.exception.BusinessError;
import com.promox.core.infrastructure.exception.BusinessException;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.Map;


public final class PromotionBusinessException extends BusinessException {

    private PromotionBusinessException(
            String message,
            String code,
            List<BusinessError> errors
    ) {
        super(message, code, errors);
    }

    public static PromotionBusinessException of(
            String message,
            String code,
            @Nullable String path,
            String errorMessage,
            @Nullable Map<String, Object> errorContext
    ) {
        return new PromotionBusinessException(
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

    public static PromotionBusinessException of(
        String message,
        String code,
        @Nullable String path,
        String errorMessage
    ) {
        return of(message, code, path, errorMessage, null);
    }

    public static PromotionBusinessException of(
            String message,
            String code,
            List<BusinessError> errors
    ) {
        return new PromotionBusinessException(
                message,
                code,
                errors
        );
    }
}
