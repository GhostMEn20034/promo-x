package com.promox.core.category.dto;

import jakarta.annotation.Nullable;

public record CategoryDto(
        Long id,
        @Nullable String externalId,
        String name,
        @Nullable Long parentId,
        @Nullable ParentCategoryDto parent,
        String storefrontId
) {}
