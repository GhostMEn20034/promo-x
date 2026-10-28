package com.promox.core.category.event;

import jakarta.annotation.Nullable;


public record CategoryUpdatedEvent(
    Long id,
    String previousExternalId,
    String externalId,
    String name,
    @Nullable Long parentId,
    Long storefrontId
) {}
