package com.promox.core.product;

import java.util.Optional;
import java.util.Set;

public interface ProductLookupApi {
    Optional<ProductWithCategoriesDto> findWithCategoriesById(Long productId);

    ProductExternalIdsExistenceResult checkExistenceByExternalIdsAndStorefront(
        Set<String> productExternalIds,
        Long storefrontId
    );
}
