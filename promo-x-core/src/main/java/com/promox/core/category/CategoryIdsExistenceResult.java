package com.promox.core.category;

import java.util.Set;

public record CategoryIdsExistenceResult(
    Set<Long> existingIds,
    Set<Long> missingIds
) {
    public boolean allExist() {
        return missingIds.isEmpty();
    }

    public boolean hasMissing() {
        return !missingIds.isEmpty();
    }
}
