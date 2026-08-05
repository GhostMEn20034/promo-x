package com.promox.core.storefront.internal.service;

import com.promox.core.storefront.internal.dto.CreateStorefrontRequest;
import com.promox.core.storefront.StorefrontDto;
import com.promox.core.storefront.internal.StorefrontMapper;
import com.promox.core.storefront.internal.dto.UpdateStorefrontRequest;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class StorefrontCommandHandler {
    private final StorefrontService storefrontService;

    public StorefrontCommandHandler(StorefrontService storefrontService) {
        this.storefrontService = storefrontService;
    }

    public StorefrontDto createStorefront(CreateStorefrontRequest creationRequest) {
        return StorefrontMapper.toDto(storefrontService.createStorefront(creationRequest));
    }

    public Optional<StorefrontDto> updateStorefrontById(
            Long storefrontId,
            UpdateStorefrontRequest updateRequest
    ) {
        return storefrontService.fetchStorefrontById(storefrontId)
                .map(storefront -> {
                    storefrontService.updateStorefront(storefront, updateRequest);
                    return StorefrontMapper.toDto(storefront);
                });
    }
}
