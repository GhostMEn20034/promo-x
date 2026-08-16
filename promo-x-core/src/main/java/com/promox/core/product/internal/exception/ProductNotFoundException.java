package com.promox.core.product.internal.exception;

import com.promox.core.infrastructure.exception.BusinessError;
import com.promox.core.infrastructure.exception.BusinessException;

import java.util.List;

public final class ProductNotFoundException extends BusinessException {

    public ProductNotFoundException(Long productId) {
        super(
            "Product with id '%d' was not found.".formatted(productId),
            "PRODUCT_NOT_FOUND",
            List.of(
                new BusinessError(
                    null,
                    "PRODUCT_NOT_FOUND",
                    "Product does not exist.",
                    null
                )
            )
        );
    }
}
