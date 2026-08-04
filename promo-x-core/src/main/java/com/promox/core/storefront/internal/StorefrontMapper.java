package com.promox.core.storefront.internal;

import com.promox.core.storefront.dto.StorefrontDto;
import com.promox.core.storefront.internal.persistence.Storefront;

public final class StorefrontMapper {
    public static StorefrontDto toDto(Storefront storefrontEntity) {
        return new StorefrontDto(
                storefrontEntity.getId(),
                storefrontEntity.getName(),
                storefrontEntity.getWebsite()
        );
    }

    public static Storefront toEntity(StorefrontDto dto) {
        return new Storefront(
            dto.id(),
            dto.name(),
            dto.website()
        );
    }
}
