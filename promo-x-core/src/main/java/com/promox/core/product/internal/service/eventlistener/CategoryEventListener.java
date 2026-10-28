package com.promox.core.product.internal.service.eventlistener;

import com.promox.core.category.event.CategoryUpdatedEvent;
import com.promox.core.infrastructure.cache.BatchCacheManager;
import com.promox.core.product.internal.service.ProductLookupApiImpl;
import com.promox.core.product.internal.service.ProductService;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;


@Component
public class CategoryEventListener {
    private final ProductService productService;
    private final BatchCacheManager batchCacheManager;

    public CategoryEventListener(ProductService productService, BatchCacheManager batchCacheManager) {
        this.productService = productService;
        this.batchCacheManager = batchCacheManager;
    }

    // Products reference categories by ID; the event's externalId is already the updated one.
    @ApplicationModuleListener
    public void on(CategoryUpdatedEvent event) {
        List<String> productExternalIds = productService.findExternalIdsByCategoryId(event.id(), event.storefrontId());

        batchCacheManager.evictAll(
            ProductLookupApiImpl.PRODUCTS_WITH_CATEGORIES_CACHE,
            productExternalIds,
            externalId -> ProductLookupApiImpl.productCacheKey(event.storefrontId(), externalId)
        );
    }
}
