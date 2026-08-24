package com.promox.core.promotion.internal.service;

import com.promox.core.product.ProductExternalIdsExistenceResult;
import com.promox.core.product.ProductLookupApi;
import com.promox.core.promotion.internal.exception.PromotionBusinessException;
import jakarta.annotation.Nullable;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;


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
}
