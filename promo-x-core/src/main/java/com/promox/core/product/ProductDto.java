package com.promox.core.product;

import jakarta.annotation.Nullable;

import java.time.Instant;
import java.util.Set;

public record ProductDto(
    Long id,
    @Nullable String externalId,
    String sku,
    String name,
    Set<Long> categoryIds,
    Long storefrontId,
    Instant createdAt,
    Instant updatedAt
) {}
