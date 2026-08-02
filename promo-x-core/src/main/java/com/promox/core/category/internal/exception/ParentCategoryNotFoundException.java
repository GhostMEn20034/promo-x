package com.promox.core.category.internal.exception;

import com.promox.core.infrastructure.exception.BusinessError;
import com.promox.core.infrastructure.exception.BusinessException;

import java.util.List;


public final class ParentCategoryNotFoundException extends BusinessException {

    public ParentCategoryNotFoundException(Long parentCategoryId) {
        super(
                "Parent category with id '%d' was not found."
                        .formatted(parentCategoryId),
                "PARENT_CATEGORY_NOT_FOUND",
                List.of(
                        new BusinessError(
                                "parentId",
                                "PARENT_CATEGORY_NOT_FOUND",
                                "Parent category does not exist."
                        )
                )
        );
    }
}
