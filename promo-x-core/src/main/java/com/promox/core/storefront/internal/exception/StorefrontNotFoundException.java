package com.promox.core.storefront.internal.exception;

import com.promox.core.infrastructure.exception.BusinessError;
import com.promox.core.infrastructure.exception.BusinessException;

import java.util.List;

public final class StorefrontNotFoundException extends BusinessException {

    public StorefrontNotFoundException(Long storefrontId) {
        super(
            "Storefront with id '%d' was not found.".formatted(storefrontId),
            "STOREFRONT_NOT_FOUND",
            List.of(
                new BusinessError(
                    null,
                    "STOREFRONT_NOT_FOUND",
                    "Storefront does not exist."
                )
            )
        );
    }
}
