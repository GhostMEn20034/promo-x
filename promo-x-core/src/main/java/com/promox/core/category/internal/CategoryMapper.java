package com.promox.core.category.internal;

import com.promox.core.category.CategoryDto;
import com.promox.core.category.ParentCategoryDto;
import com.promox.core.category.internal.persistence.Category;

public final class CategoryMapper {
    private CategoryMapper() {}

    /*
     * WARNING:
     * Assumes the parent category is already loaded. Otherwise, accessing
     * the parent may trigger an additional database query due to lazy loading.
     */
    public static CategoryDto toDto(Category entity) {
        return new CategoryDto(
                entity.getId(),
                entity.getExternalId(),
                entity.getName(),
                entity.getParent() != null ? new ParentCategoryDto(
                        entity.getParent().getId(),
                        entity.getParent().getExternalId(),
                        entity.getParent().getName(),
                        entity.getParent().getParentId(),
                        entity.getParent().getStorefrontId()
                ) : null,
                entity.getStorefrontId()
        );
    }
}
