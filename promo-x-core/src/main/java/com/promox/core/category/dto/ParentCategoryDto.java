package com.promox.core.category.dto;

import jakarta.annotation.Nullable;

public record ParentCategoryDto(
        Long id,
        @Nullable String externalId,
        String name,
        @Nullable Long parentId,
        String storefrontId
) {}
