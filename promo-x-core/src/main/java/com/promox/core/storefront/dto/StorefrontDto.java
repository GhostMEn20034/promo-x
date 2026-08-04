package com.promox.core.storefront.dto;

import jakarta.annotation.Nullable;

public record StorefrontDto(
        Long id,
        String name,
        @Nullable String website
) {}