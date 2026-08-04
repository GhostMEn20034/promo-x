package com.promox.core.storefront.internal.service;

import com.promox.core.storefront.internal.dto.CreateStorefrontRequest;
import com.promox.core.storefront.dto.StorefrontDto;
import com.promox.core.storefront.internal.StorefrontMapper;
import org.springframework.stereotype.Service;

@Service
public class StorefrontCommandHandler {
    private final StorefrontService storefrontService;

    public StorefrontCommandHandler(StorefrontService storefrontService) {
        this.storefrontService = storefrontService;
    }

    public StorefrontDto createStorefront(CreateStorefrontRequest creationRequest) {
        return StorefrontMapper.toDto(storefrontService.createStorefront(creationRequest));
    }
}
