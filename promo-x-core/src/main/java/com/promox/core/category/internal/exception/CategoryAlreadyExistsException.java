package com.promox.core.category.internal.exception;

import com.promox.core.infrastructure.exception.BusinessError;
import com.promox.core.infrastructure.exception.BusinessException;
import jakarta.annotation.Nullable;


import java.util.List;

public final class CategoryAlreadyExistsException extends BusinessException {
    /**
     * Creates an exception indicating that a category already exists.
     *
     * @param message friendly error message describing the failure.
     * @param lookupField name of the field that caused the conflict
     *                    (for example, {@code "id"} or {@code "externalId"}).
     */
    public CategoryAlreadyExistsException(
            String message,
            @Nullable String lookupField
    ) {
        super(
                message,
                "CATEGORY_ALREADY_EXISTS",
                List.of(
                        new BusinessError(
                                lookupField,
                                "CATEGORY_ALREADY_EXISTS",
                                message
                        )
                )
        );
    }
}
