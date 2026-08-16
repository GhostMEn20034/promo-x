package com.promox.core.product.internal.service;

import com.promox.core.infrastructure.exception.BusinessError;
import com.promox.core.product.internal.dto.CreateProductRequest;
import com.promox.core.product.internal.exception.ProductBusinessException;
import com.promox.core.product.internal.persistence.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.*;


@Service
public class ProductValidator {
    private final ProductRepository productRepository;

    public ProductValidator(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * Validates external IDs across a batch of product creation requests:
     * <ol>
     *  <li>Checks for duplicates within the request body.</li>
     *  <li>Checks for existing external IDs in the database for the given storefront.</li>
     * </ol>
     */
    public void validateExternalIdsUniquenessForBulk(
        List<CreateProductRequest> items,
        Long storefrontId
    ) {
        if (items.isEmpty()) {
            return;
        }

        List<BusinessError> errors = new ArrayList<>();

        // 1. Group non-null external IDs by their item indices in the request
        Map<String, List<Integer>> externalIdIndices = collectExternalIdIndices(items);

        // 2. Collect errors for duplicate external IDs within the batch
        collectBatchDuplicateErrors(externalIdIndices, errors);

        // 3. Check database for existing external IDs (1 query)
        Set<String> distinctExternalIds = externalIdIndices.keySet();
        if (!distinctExternalIds.isEmpty()) {
            Set<String> existingDbExternalIds = productRepository.findExistingExternalIds(
                distinctExternalIds,
                storefrontId
            );

            if (!existingDbExternalIds.isEmpty()) {
                collectDbExistingErrors(items, existingDbExternalIds, errors);
            }
        }

        // 4. Throw exception if any batch or DB errors were detected
        if (!errors.isEmpty()) {
            throw ProductBusinessException.of(
                "Validation failed: external ID conflicts found in batch request",
                "EXTERNAL_ID_VALIDATION_FAILED",
                errors
            );
        }
    }

    private Map<String, List<Integer>> collectExternalIdIndices(List<CreateProductRequest> items) {
        Map<String, List<Integer>> externalIdIndices = new HashMap<>();
        for (int i = 0; i < items.size(); i++) {
            String externalId = items.get(i).externalId();
            if (externalId != null) {
                externalIdIndices.computeIfAbsent(externalId, k -> new ArrayList<>()).add(i);
            }
        }
        return externalIdIndices;
    }

    private void collectBatchDuplicateErrors(
        Map<String, List<Integer>> externalIdIndices,
        List<BusinessError> errors
    ) {
        for (Map.Entry<String, List<Integer>> entry : externalIdIndices.entrySet()) {
            List<Integer> indices = entry.getValue();
            if (indices.size() > 1) {
                String duplicateId = entry.getKey();

                // Flag subsequent duplicate occurrences (from index 1 onwards)
                for (int i = 1; i < indices.size(); i++) {
                    int duplicateIndex = indices.get(i);
                    errors.add(new BusinessError(
                        "items[%d].externalId".formatted(duplicateIndex),
                        "DUPLICATE_EXTERNAL_ID_IN_BATCH",
                        "External ID '%s' is duplicated within the request batch".formatted(duplicateId),
                        Map.of(
                            "externalId", duplicateId,
                            "firstOccurredAtIndex", indices.getFirst()
                        )
                    ));
                }
            }
        }
    }

    private void collectDbExistingErrors(
        List<CreateProductRequest> items,
        Set<String> existingDbExternalIds,
        List<BusinessError> errors
    ) {
        for (int i = 0; i < items.size(); i++) {
            String externalId = items.get(i).externalId();
            if (externalId != null && existingDbExternalIds.contains(externalId)) {
                errors.add(new BusinessError(
                    "items[%d].externalId".formatted(i),
                    "EXTERNAL_ID_ALREADY_EXISTS",
                    "External ID '%s' already exists for this storefront".formatted(externalId),
                    Map.of(
                        "externalId", externalId,
                        "sku", items.get(i).sku()
                    )
                ));
            }
        }
    }
}
