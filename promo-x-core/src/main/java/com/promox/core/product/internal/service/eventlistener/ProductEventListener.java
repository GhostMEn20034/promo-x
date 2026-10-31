package com.promox.core.product.internal.service.eventlistener;

import com.promox.core.infrastructure.cache.BatchCacheManager;
import com.promox.core.product.event.ProductUpdatedEvent;
import com.promox.core.product.internal.service.ProductLookupApiImpl;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;


@Component
public class ProductEventListener {
    private final BatchCacheManager batchCacheManager;

    public ProductEventListener(BatchCacheManager batchCacheManager) {
        this.batchCacheManager = batchCacheManager;
    }

    // Cache keys are external IDs, so a changed external ID leaves the entry under the previous one.
    @ApplicationModuleListener
    public void on(ProductUpdatedEvent event) {
        batchCacheManager.evictAll(
            ProductLookupApiImpl.PRODUCTS_WITH_CATEGORIES_CACHE,
            List.of(event.previousExternalId(), event.externalId()),
            externalId -> ProductLookupApiImpl.productCacheKey(event.storefrontId(), externalId)
        );
    }
}
