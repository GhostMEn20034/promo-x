package com.promox.core.category;

import java.util.Set;

public record CategoryExternalIdsExistenceResult(
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
