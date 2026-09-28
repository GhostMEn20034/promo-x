package com.promox.core.product.internal.service.eventlistener;

import com.promox.core.category.event.CategoryUpdatedEvent;
import com.promox.core.product.internal.service.ProductService;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;


@Component
public class CategoryEventListener {
    private final ProductService productService;

    public CategoryEventListener(ProductService productService) {
        this.productService = productService;
    }

    @ApplicationModuleListener
    public void on(CategoryUpdatedEvent event) {

    }
}
