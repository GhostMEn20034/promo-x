package com.promox.core.product.internal.web;

import com.promox.core.product.DefaultProductDto;
import com.promox.core.product.ProductLookupApi;
import com.promox.core.product.ProductWithCategoriesDto;
import com.promox.core.product.internal.dto.CreateSingleProductRequest;
import com.promox.core.product.internal.exception.ProductBusinessException;
import com.promox.core.product.internal.exception.ProductNotFoundException;
import com.promox.core.product.internal.service.ProductCommandHandler;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/product")
public class ProductController {
    private final ProductCommandHandler productCommandHandler;
    private final ProductLookupApi productLookupApi;

    public ProductController(ProductCommandHandler productCommandHandler, ProductLookupApi productLookupApi) {
        this.productCommandHandler = productCommandHandler;
        this.productLookupApi = productLookupApi;
    }

    @PostMapping("/")
    @ResponseStatus(HttpStatus.CREATED)
    public DefaultProductDto createSingleProduct(
        @RequestHeader("X-Storefront-Id") Long storefrontId,
        @Valid @RequestBody CreateSingleProductRequest creationRequest
    ) {
        return productCommandHandler.createSingleProduct(storefrontId, creationRequest);
    }

    @GetMapping("/{productId}")
    public ProductWithCategoriesDto getProductId(@PathVariable Long productId) {
        return productLookupApi.findWithCategoriesById(productId)
            .orElseThrow(() -> new ProductNotFoundException(productId));
    }
}
