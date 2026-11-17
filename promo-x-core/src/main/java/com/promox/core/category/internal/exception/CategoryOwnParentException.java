package com.promox.core.category.internal.exception;

import com.promox.core.infrastructure.exception.BusinessError;
import com.promox.core.infrastructure.exception.BusinessException;

import java.util.List;


public final class CategoryOwnParentException extends BusinessException {

    public CategoryOwnParentException(Long categoryId) {
        super(
            "Category with id '%d' cannot be its own parent."
                    .formatted(categoryId),
            "CATEGORY_CANNOT_BE_OWN_PARENT",
            List.of(
                new BusinessError(
                    "parentId",
                    "CATEGORY_CANNOT_BE_OWN_PARENT",
                    "A category cannot be its own parent.",
                    null
                )
            )
        );
    }
}
