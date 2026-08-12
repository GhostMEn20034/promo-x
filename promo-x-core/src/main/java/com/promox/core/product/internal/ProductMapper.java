package com.promox.core.product.internal;

import com.promox.core.product.ProductDto;
import com.promox.core.product.internal.persistence.Product;

import java.util.Set;

public class ProductMapper {
    private ProductMapper() {}

    public static ProductDto toDto(Product entity) {
        // Defensive copy prevents accidental mutation of Hibernate's PersistentSet
        var categoryIds = Set.copyOf(entity.getCategoryIds());

        return new ProductDto(
            entity.getId(),
            entity.getExternalId(),
            entity.getSku(),
            entity.getName(),
            categoryIds,
            entity.getStorefrontId(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
