package com.promox.core.category;

import jakarta.annotation.Nullable;

public record CategoryDto(
        Long id,
        @Nullable String externalId,
        String name,
        @Nullable ParentCategoryDto parent,
        Long storefrontId
) {}
