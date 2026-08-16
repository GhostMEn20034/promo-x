package com.promox.core.product.internal.web;

import com.promox.core.product.DefaultProductDto;
import com.promox.core.product.ProductLookupApi;
import com.promox.core.product.ProductWithCategoriesDto;
import com.promox.core.product.internal.dto.CreateMultipleProductsRequest;
import com.promox.core.product.internal.dto.CreateProductRequest;
import com.promox.core.product.internal.exception.ProductNotFoundException;
import com.promox.core.product.internal.service.ProductCommandHandler;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;


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
        @Valid @RequestBody CreateProductRequest creationRequest
    ) {
        return productCommandHandler.createSingleProduct(storefrontId, creationRequest);
    }

    @PostMapping("/bulk-insert")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void createMultipleProducts(
        @RequestHeader("X-Storefront-Id") Long storefrontId,
        @Valid @RequestBody CreateMultipleProductsRequest multipleProductsRequest
    ) {
        productCommandHandler.createMultipleProducts(storefrontId, multipleProductsRequest);
    }

    @GetMapping("/{productId}")
    public ProductWithCategoriesDto getProductId(@PathVariable Long productId) {
        return productLookupApi.findWithCategoriesById(productId)
            .orElseThrow(() -> new ProductNotFoundException(productId));
    }
}
