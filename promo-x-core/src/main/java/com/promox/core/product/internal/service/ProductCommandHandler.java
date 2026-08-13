package com.promox.core.product.internal.service;

import com.promox.core.category.CategoryIdsExistenceResult;
import com.promox.core.category.CategoryLookupApi;
import com.promox.core.product.DefaultProductDto;
import com.promox.core.product.internal.ProductMapper;
import com.promox.core.product.internal.dto.CreateSingleProductRequest;
import com.promox.core.product.internal.exception.ProductBusinessException;

import jakarta.annotation.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;


@Service
public class ProductCommandHandler {
    private final ProductService productService;
    private final CategoryLookupApi categoryLookupApi;

    public ProductCommandHandler(ProductService productService, CategoryLookupApi categoryLookupApi) {
        this.productService = productService;
        this.categoryLookupApi = categoryLookupApi;
    }

    @Transactional
    public DefaultProductDto createSingleProduct(Long storefrontId, CreateSingleProductRequest creationRequest) {
        validateCategoryIdsExistence(creationRequest.categoryIds(), storefrontId, "categoryIds");
        return ProductMapper.toDto(productService.createSingleProduct(storefrontId, creationRequest));
    }

    /**
     * Validates that all requested category IDs exist in the system.
     *
     * @param categoryIds the set of category IDs to validate for existence
     * @param path        the field path or property name used for validation error tracking (e.g., "categoryIds")
     * @throws ProductBusinessException if one or more provided category IDs do not exist
     */
    private void validateCategoryIdsExistence(
        Set<Long> categoryIds,
        Long storefrontId,
        @Nullable String path
    ) {
        if (categoryIds.isEmpty()) {
            return;
        }

        CategoryIdsExistenceResult existenceResult = categoryLookupApi.checkExistenceByIdsAndStorefront(
            categoryIds,
            storefrontId
        );

        if (existenceResult.allExist()) {
            return;
        }

        throw ProductBusinessException.of(
            "Validation failed: specified category IDs do not exist",
            "CATEGORY_IDS_NOT_FOUND",
            path,
            "One or more category IDs do not exist",
            Map.of(
                "missingCategoryIds", existenceResult.missingIds(),
                "existingCategoryIds", existenceResult.existingIds()
            )
        );
    }
}
