package com.promox.core.category.internal.exception;

import com.promox.core.infrastructure.exception.BusinessError;
import com.promox.core.infrastructure.exception.BusinessException;
import jakarta.annotation.Nullable;

import java.util.List;

public final class CategoryBusinessException extends BusinessException {

    private CategoryBusinessException(
            String message,
            String code,
            List<BusinessError> errors
    ) {
        super(message, code, errors);
    }

    public static CategoryBusinessException of(
            String message,
            String code,
            @Nullable String path,
            String errorMessage
    ) {
        return new CategoryBusinessException(
                message,
                code,
                List.of(
                        new BusinessError(
                                path,
                                code,
                                errorMessage
                        )
                )
        );
    }

    public static CategoryBusinessException of(
            String message,
            String code,
            List<BusinessError> errors
    ) {
        return new CategoryBusinessException(
                message,
                code,
                errors
        );
    }
}
