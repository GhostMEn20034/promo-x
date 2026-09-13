package com.promox.core.storefront;

import java.util.Optional;

public interface StorefrontLookupApi {
    Optional<StorefrontDto> findById(Long storefrontId);
    boolean existsById(Long storefrontId);
}