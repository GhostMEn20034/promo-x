package com.promox.core.storefront;

import com.promox.core.storefront.dto.StorefrontDto;
import com.promox.core.storefront.internal.StorefrontMapper;
import com.promox.core.storefront.internal.service.StorefrontService;
import org.springframework.stereotype.Service;

import java.util.Optional;


@Service
public class StorefrontLookupApi {
    private final StorefrontService storefrontService;

    public StorefrontLookupApi(StorefrontService storefrontService) {
        this.storefrontService = storefrontService;
    }

    public Optional<StorefrontDto> findStorefrontById(Long storefrontId) {
        return storefrontService.fetchStorefrontById(storefrontId)
                .map(StorefrontMapper::toDto);
    }
}
