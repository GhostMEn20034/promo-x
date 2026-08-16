package com.promox.core.product.internal.service;

import com.promox.core.category.internal.exception.CategoryBusinessException;
import com.promox.core.product.DefaultProductDto;
import com.promox.core.product.internal.ProductMapper;
import com.promox.core.product.internal.dto.CreateMultipleProductsRequest;
import com.promox.core.product.internal.dto.CreateProductRequest;

import com.promox.core.product.internal.exception.ProductBusinessException;
import com.promox.core.storefront.StorefrontLookupApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Service
public class ProductCommandHandler {
    private final ProductService productService;
    private final ProductCategoryValidator productCategoryValidator;
    private final ProductValidator productValidator;
    private final StorefrontLookupApi storefrontLookupApi;

    public ProductCommandHandler(
        ProductService productService,
        ProductCategoryValidator productCategoryValidator,
        ProductValidator productValidator,
        StorefrontLookupApi storefrontLookupApi
    ) {
        this.productService = productService;
        this.productCategoryValidator = productCategoryValidator;
        this.productValidator = productValidator;
        this.storefrontLookupApi = storefrontLookupApi;
    }

    @Transactional
    public DefaultProductDto createSingleProduct(Long storefrontId, CreateProductRequest creationRequest) {
        if (!storefrontLookupApi.existsById(storefrontId)) {
            throw ProductBusinessException.of(
                "Storefront does not exist.",
                "INVALID_STOREFRONT",
                null,
                "The specified storefront does not exist.",
                null
            );
        }
        productCategoryValidator.validateCategoryIdsExistence(creationRequest.categoryIds(), storefrontId, "categoryIds");
        return ProductMapper.toDto(productService.createSingleProduct(storefrontId, creationRequest));
    }

    @Transactional
    public void createMultipleProducts(
        Long storefrontId,
        CreateMultipleProductsRequest creationRequest
    ) {
        if (!storefrontLookupApi.existsById(storefrontId)) {
            throw ProductBusinessException.of(
                "Storefront does not exist.",
                "INVALID_STOREFRONT",
                null,
                "The specified storefront does not exist.",
                null
            );
        }

        productCategoryValidator.validateCategoryIdsExistenceForBulk(creationRequest.items(), storefrontId);
        productValidator.validateExternalIdsUniquenessForBulk(creationRequest.items(), storefrontId);
        productService.createMultipleProducts(storefrontId, creationRequest.items());
    }
}
