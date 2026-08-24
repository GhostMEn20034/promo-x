package com.promox.core.product;

import java.util.Set;


public record ProductExternalIdsExistenceResult(
    Set<String> existingIds,
    Set<String> missingIds
) {
    public boolean allExist() {
        return missingIds.isEmpty();
    }

    public boolean hasMissing() {
        return !missingIds.isEmpty();
    }
}