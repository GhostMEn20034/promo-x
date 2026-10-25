package com.promox.core.product.internal.web;

import com.promox.core.infrastructure.security.Roles;
import com.promox.core.product.DefaultProductDto;
import com.promox.core.product.ProductLookupApi;
import com.promox.core.product.ProductWithCategoriesDto;
import com.promox.core.product.internal.dto.CreateMultipleProductsRequest;
import com.promox.core.product.internal.dto.CreateProductRequest;
import com.promox.core.product.internal.dto.UpdateProductRequest;
import com.promox.core.product.internal.exception.ProductNotFoundException;
import com.promox.core.product.internal.service.ProductCommandHandler;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;


@RestController
@RequestMapping("/product")
@PreAuthorize("hasRole('" + Roles.EMPLOYEE + "')")
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

    @PutMapping("/{productId}")
    @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
    public DefaultProductDto updateProduct(
        @PathVariable Long productId,
        @Valid @RequestBody UpdateProductRequest updateRequest
    ) {
        return productCommandHandler.updateById(productId, updateRequest)
            .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    @GetMapping("/{productId}")
    @PreAuthorize("hasAnyRole('" + Roles.EMPLOYEE + "', '" + Roles.AUDITOR + "')")
    public ProductWithCategoriesDto getProductById(@PathVariable Long productId) {
        return productLookupApi.findWithCategoriesById(productId)
            .orElseThrow(() -> new ProductNotFoundException(productId));
    }
}
