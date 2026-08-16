package com.promox.core.category.internal.exception;

import com.promox.core.infrastructure.exception.BusinessError;
import com.promox.core.infrastructure.exception.BusinessException;

import java.util.List;

public final class CategoryNotFoundException extends BusinessException {

    public CategoryNotFoundException(Long categoryId) {
        super(
            "Category with id '%d' was not found.".formatted(categoryId),
            "CATEGORY_NOT_FOUND",
            List.of(
                new BusinessError(
                    null,
                    "CATEGORY_NOT_FOUND",
                    "Category does not exist.",
                    null
                )
            )
        );
    }
}
