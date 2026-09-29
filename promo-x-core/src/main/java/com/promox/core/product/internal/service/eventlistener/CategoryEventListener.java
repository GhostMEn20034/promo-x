package com.promox.core.product.internal.service.eventlistener;

import com.promox.core.category.event.CategoryUpdatedEvent;
import com.promox.core.infrastructure.cache.RedisBatchCache;
import com.promox.core.product.internal.service.ProductLookupApiImpl;
import com.promox.core.product.internal.service.ProductService;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;


@Component
public class CategoryEventListener {
    private final ProductService productService;
    private final RedisBatchCache redisBatchCache;

    public CategoryEventListener(ProductService productService, RedisBatchCache redisBatchCache) {
        this.productService = productService;
        this.redisBatchCache = redisBatchCache;
    }

    // Products reference categories by ID; the event's externalId is already the updated one.
    @ApplicationModuleListener
    public void on(CategoryUpdatedEvent event) {
        List<String> productExternalIds = productService.findExternalIdsByCategoryId(event.id(), event.storefrontId());

        redisBatchCache.evictAll(
            ProductLookupApiImpl.PRODUCTS_WITH_CATEGORIES_CACHE,
            productExternalIds,
            externalId -> ProductLookupApiImpl.productCacheKey(event.storefrontId(), externalId)
        );
    }
}
