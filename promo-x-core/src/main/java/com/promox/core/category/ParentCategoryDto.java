package com.promox.core.category;

import jakarta.annotation.Nullable;

public record ParentCategoryDto(
        Long id,
        @Nullable String externalId,
        String name,
        @Nullable Long parentId,
        Long storefrontId
) {}
