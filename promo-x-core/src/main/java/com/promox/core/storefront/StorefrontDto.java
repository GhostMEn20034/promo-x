package com.promox.core.storefront;

import jakarta.annotation.Nullable;

public record StorefrontDto(
        Long id,
        String name,
        @Nullable String website
) {}