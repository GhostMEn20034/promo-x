package com.promox.core.product.event;

import java.time.Instant;
import java.util.Set;

/**
 * @param previousExternalId external ID before the update; differs from {@code externalId} when it was changed
 */
public record ProductUpdatedEvent(
        Long id,
        String previousExternalId,
        String externalId,
        String sku,
        String name,
        Set<Long> categoryIds,
        Long storefrontId,
        Instant updatedAt
) {}
