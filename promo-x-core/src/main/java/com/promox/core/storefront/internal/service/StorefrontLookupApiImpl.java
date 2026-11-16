package com.promox.core.storefront.internal.service;

import com.promox.core.storefront.StorefrontDto;
import com.promox.core.storefront.StorefrontLookupApi;
import com.promox.core.storefront.internal.StorefrontMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Optional;


@Service
public class StorefrontLookupApiImpl implements StorefrontLookupApi {
    private final StorefrontService storefrontService;

    public StorefrontLookupApiImpl(StorefrontService storefrontService) {
        this.storefrontService = storefrontService;
    }

    public Optional<StorefrontDto> findById(Long storefrontId) {
        return storefrontService.fetchById(storefrontId)
                .map(StorefrontMapper::toDto);
    }

    public boolean existsById(Long storefrontId) {
        return storefrontService.existsById(storefrontId);
    }

    @Override
    public Page<StorefrontDto> findAll(Pageable pageable) {
        return storefrontService.findAll(pageable)
                .map(StorefrontMapper::toDto);
    }
}
