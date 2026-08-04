package com.promox.core.storefront.internal.web;

import com.promox.core.storefront.StorefrontLookupApi;
import com.promox.core.storefront.internal.dto.CreateStorefrontRequest;
import com.promox.core.storefront.dto.StorefrontDto;
import com.promox.core.storefront.internal.exception.StorefrontNotFoundException;
import com.promox.core.storefront.internal.service.StorefrontCommandHandler;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/storefront")
public class StorefrontController {
    private final StorefrontCommandHandler storefrontCommandHandler;
    private final StorefrontLookupApi storefrontLookupApi;

    public StorefrontController(
            StorefrontCommandHandler storefrontCommandHandler,
            StorefrontLookupApi storefrontLookupApi
    ) {
        this.storefrontCommandHandler = storefrontCommandHandler;
        this.storefrontLookupApi = storefrontLookupApi;
    }

    @PostMapping("/")
    @ResponseStatus(HttpStatus.CREATED)
    public StorefrontDto createStorefront(
            @Valid @RequestBody CreateStorefrontRequest requestBody
    ) {
        return storefrontCommandHandler.createStorefront(requestBody);
    }

    @GetMapping("/{storefrontId}")
    public StorefrontDto getStorefrontById(@PathVariable Long storefrontId) {
        return storefrontLookupApi.findStorefrontById(storefrontId)
                .orElseThrow(() ->
                        new StorefrontNotFoundException(storefrontId)
                );
    }
}
