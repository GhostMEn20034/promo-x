package com.promox.core.storefront.internal.service;

import com.promox.core.storefront.internal.dto.CreateStorefrontRequest;
import com.promox.core.storefront.internal.dto.UpdateStorefrontRequest;
import com.promox.core.storefront.internal.persistence.Storefront;
import com.promox.core.storefront.internal.persistence.StorefrontRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;


@Service
public class StorefrontService {
    private final StorefrontRepository storefrontRepository;

    public StorefrontService(StorefrontRepository storefrontRepository) {
        this.storefrontRepository = storefrontRepository;
    }

    @Transactional
    public Storefront createStorefront(CreateStorefrontRequest creationRequest) {
        Storefront storefront = Storefront.builder()
                .name(creationRequest.name())
                .website(creationRequest.website())
                .build();

        return storefrontRepository.save(storefront);
    }

    @Transactional(readOnly = true)
    public Optional<Storefront> fetchStorefrontById(Long storefrontId) {
        return storefrontRepository.findById(storefrontId);
    }

    @Transactional(readOnly = true)
    public boolean existsById(Long storefrontId) {
        return storefrontRepository.existsById(storefrontId);
    }

    @Transactional
    public void updateStorefront(
            Storefront storefrontToUpdate,
            UpdateStorefrontRequest dataToApply
    ) {
        storefrontToUpdate.setName(dataToApply.name());
        storefrontToUpdate.setWebsite(dataToApply.website());
    }
}
