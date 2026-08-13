package com.promox.core.product;

import jakarta.annotation.Nullable;

import java.time.Instant;


public record ProductBaseDto(
    Long id,
    @Nullable String externalId,
    String sku,
    String name,
    Long storefrontId,
    Instant createdAt,
    Instant updatedAt
) {}
