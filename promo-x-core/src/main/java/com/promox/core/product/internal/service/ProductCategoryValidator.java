package com.promox.core.product.internal.service;

import com.promox.core.category.CategoryIdsExistenceResult;
import com.promox.core.category.CategoryLookupApi;
import com.promox.core.infrastructure.exception.BusinessError;
import com.promox.core.product.internal.dto.CreateProductRequest;
import com.promox.core.product.internal.exception.ProductBusinessException;
import jakarta.annotation.Nullable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProductCategoryValidator {
    private final CategoryLookupApi categoryLookupApi;

    public ProductCategoryValidator(CategoryLookupApi categoryLookupApi) {
        this.categoryLookupApi = categoryLookupApi;
    }

    /**
     * Validates that all requested category IDs exist in the system.
     *
     * @param categoryIds the set of category IDs to validate for existence
     * @param path        the field path or property name used for validation error tracking (e.g., "categoryIds")
     * @throws ProductBusinessException if one or more provided category IDs do not exist
     */
    void validateCategoryIdsExistence(
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

    /**
     * Validates category existence across a batch of product creation requests.
     */
    public void validateCategoryIdsExistenceForBulk(
        List<CreateProductRequest> items,
        Long storefrontId
    ) {
        if (items.isEmpty()) {
            return;
        }

        Set<Long> allCategoryIds = extractAllCategoryIds(items);
        if (allCategoryIds.isEmpty()) {
            return;
        }

        CategoryIdsExistenceResult existenceResult = categoryLookupApi.checkExistenceByIdsAndStorefront(
            allCategoryIds,
            storefrontId
        );

        if (existenceResult.allExist()) {
            return;
        }

        List<BusinessError> errors = collectBatchCategoryErrors(items, existenceResult.missingIds());
        if (!errors.isEmpty()) {
            throw ProductBusinessException.of(
                "Validation failed: specified category IDs do not exist for one or more batch items",
                "CATEGORY_IDS_NOT_FOUND",
                errors
            );
        }
    }

    private Set<Long> extractAllCategoryIds(List<CreateProductRequest> items) {
        return items.stream()
            .flatMap(item -> item.categoryIds().stream())
            .collect(Collectors.toSet());
    }

    private List<BusinessError> collectBatchCategoryErrors(
        List<CreateProductRequest> items,
        Set<Long> globalMissingIds
    ) {
        List<BusinessError> errors = new ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
            buildItemCategoryError(i, items.get(i).categoryIds(), globalMissingIds)
                .ifPresent(errors::add);
        }

        return errors;
    }

    private Optional<BusinessError> buildItemCategoryError(
        int index,
        Set<Long> itemCategoryIds,
        Set<Long> globalMissingIds
    ) {
        if (itemCategoryIds.isEmpty()) {
            return Optional.empty();
        }

        Set<Long> missingCategoryIds = itemCategoryIds.stream()
            .filter(globalMissingIds::contains)
            .collect(Collectors.toSet());

        if (missingCategoryIds.isEmpty()) {
            return Optional.empty();
        }

        Set<Long> existingCategoryIds = itemCategoryIds.stream()
            .filter(id -> !globalMissingIds.contains(id))
            .collect(Collectors.toSet());

        return Optional.of(new BusinessError(
            "items[%d].categoryIds".formatted(index),
            "CATEGORY_IDS_NOT_FOUND",
            "One or more category IDs do not exist",
            Map.of(
                "missingCategoryIds", missingCategoryIds,
                "existingCategoryIds", existingCategoryIds
            )
        ));
    }
}
