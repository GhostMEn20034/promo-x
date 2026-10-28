package com.promox.core.promotion.internal.service;

import com.promox.core.category.CategoryExternalIdsExistenceResult;
import com.promox.core.category.CategoryLookupApi;
import com.promox.core.promotion.internal.exception.PromotionBusinessException;
import jakarta.annotation.Nullable;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;


@Service
public class PromotionCategoryValidator {
    private final CategoryLookupApi categoryLookupApi;

    public PromotionCategoryValidator(CategoryLookupApi categoryLookupApi) {
        this.categoryLookupApi = categoryLookupApi;
    }

    /**
     * Validates that all requested category external IDs exist in the storefront.
     *
     * @param categoryExternalIds the set of category external IDs to validate for existence
     * @param storefrontId        the storefront the categories must belong to
     * @param path                the field path or property name used for validation error tracking
     * @throws PromotionBusinessException if one or more provided category external IDs do not exist
     */
    void validateCategoryExternalIdsExistence(
        Set<String> categoryExternalIds,
        Long storefrontId,
        @Nullable String path
    ) {
        if (categoryExternalIds.isEmpty()) {
            return;
        }

        CategoryExternalIdsExistenceResult existenceResult = categoryLookupApi.checkExistenceByExternalIdsAndStorefront(
            categoryExternalIds,
            storefrontId
        );

        if (existenceResult.allExist()) {
            return;
        }

        throw PromotionBusinessException.of(
            "Validation failed: specified category external IDs do not exist",
            "CATEGORY_EXTERNAL_IDS_NOT_FOUND",
            path,
            "One or more category external IDs do not exist",
            Map.of(
                "missingCategoryExternalIds", existenceResult.missingIds(),
                "existingCategoryExternalIds", existenceResult.existingIds()
            )
        );
    }
}
