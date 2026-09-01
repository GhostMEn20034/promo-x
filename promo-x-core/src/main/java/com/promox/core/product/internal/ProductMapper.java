package com.promox.core.product.internal;

import com.promox.core.category.CategoryDto;
import com.promox.core.product.DefaultProductDto;
import com.promox.core.product.ProductBaseDto;
import com.promox.core.product.ProductWithCategoriesDto;
import com.promox.core.product.internal.persistence.Product;

import java.util.Collection;
import java.util.Set;


public final class ProductMapper {
    private ProductMapper() {}

    public static DefaultProductDto toDto(Product entity) {
        // Defensive copy prevents accidental mutation of Hibernate's PersistentSet
        var categoryIds = Set.copyOf(entity.getCategoryIds());

        return new DefaultProductDto(
            new ProductBaseDto(
                entity.getId(),
                entity.getExternalId(),
                entity.getSku(),
                entity.getName(),
                entity.getStorefrontId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
            ),
            categoryIds
        );
    }

    public static ProductWithCategoriesDto toDto(Product entity, Collection<CategoryDto> categoryDTOs) {
        return new ProductWithCategoriesDto(
            new ProductBaseDto(
                entity.getId(),
                entity.getExternalId(),
                entity.getSku(),
                entity.getName(),
                entity.getStorefrontId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
            ),
            Set.copyOf(categoryDTOs)
        );
    }
}
