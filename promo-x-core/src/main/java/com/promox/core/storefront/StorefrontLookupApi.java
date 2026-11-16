package com.promox.core.storefront;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface StorefrontLookupApi {
    Optional<StorefrontDto> findById(Long storefrontId);
    boolean existsById(Long storefrontId);
    Page<StorefrontDto> findAll(Pageable pageable);
}