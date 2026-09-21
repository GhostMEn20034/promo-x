package com.promox.core.promotion.internal.service;

import com.promox.core.product.ProductExternalIdsExistenceResult;
import com.promox.core.product.ProductLookupApi;
import com.promox.core.product.ProductWithCategoriesDto;
import com.promox.core.promotion.internal.exception.PromotionBusinessException;
import jakarta.annotation.Nullable;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


@Service
public class PromotionProductValidator {
    private final ProductLookupApi productLookupApi;

    public PromotionProductValidator(ProductLookupApi productLookupApi) {
        this.productLookupApi = productLookupApi;
    }


    /**
     * Validates that all requested product external IDs exist in the system.
     *
     * @param productExternalIds the set of product external IDs to validate for existence
     * @param path        the field path or property name used for validation error tracking (e.g., "productExternalId")
     * @throws PromotionBusinessException if one or more provided product external IDs do not exist
     */
    void validateProductExternalIdsExistence(
        Set<String> productExternalIds,
        Long storefrontId,
        @Nullable String path
    ) {
        if (productExternalIds.isEmpty()) {
            return;
        }

        ProductExternalIdsExistenceResult existenceResult = productLookupApi.checkExistenceByExternalIdsAndStorefront(
            productExternalIds,
            storefrontId
        );

        if (existenceResult.allExist()) {
            return;
        }

        throw PromotionBusinessException.of(
            "Validation failed: specified product external IDs do not exist",
            "PRODUCT_EXTERNAL_IDS_NOT_FOUND",
            path,
            "One or more product external IDs do not exist",
            Map.of(
                "missingProductExternalIds", existenceResult.missingIds(),
                "existingProductExternalIds", existenceResult.existingIds()
            )
        );
    }

    /**
     * Fetches products (with categories) for the given external IDs and validates that
     * every requested external ID resolved to a product within the storefront.
     *
     * @param productExternalIds the set of product external IDs referenced by the order
     * @param storefrontId       the storefront the products must belong to
     * @param path                the field path or property name used for validation error tracking (e.g., "items")
     * @return the fetched products, one per requested external ID
     * @throws PromotionBusinessException if one or more provided product external IDs do not exist
     */
    List<ProductWithCategoriesDto> validateAndFetchOrderProducts(
        Set<String> productExternalIds,
        Long storefrontId,
        @Nullable String path
    ) {
        if (productExternalIds.isEmpty()) {
            return List.of();
        }

        List<ProductWithCategoriesDto> products = productLookupApi.findAllWithCategoriesByExternalIds(
            productExternalIds,
            storefrontId
        );

        Set<String> existingIds = products.stream()
            .map(product -> product.base().externalId())
            .collect(Collectors.toSet());
        Set<String> missingIds = new HashSet<>(productExternalIds);
        missingIds.removeAll(existingIds);

        ProductExternalIdsExistenceResult existenceResult = new ProductExternalIdsExistenceResult(existingIds, missingIds);

        if (existenceResult.allExist()) {
            return products;
        }

        throw PromotionBusinessException.of(
            "Validation failed: specified product external IDs do not exist",
            "PRODUCT_EXTERNAL_IDS_NOT_FOUND",
            path,
            "One or more product external IDs do not exist",
            Map.of(
                "missingProductExternalIds", existenceResult.missingIds(),
                "existingProductExternalIds", existenceResult.existingIds()
            )
        );
    }
}
