package com.promox.core.product;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ProductLookupApi {
    Optional<ProductWithCategoriesDto> findWithCategoriesById(Long productId);

    List<DefaultProductDto> findAllByExternalIds(Set<String> externalIds, Long storefrontId);

    ProductExternalIdsExistenceResult checkExistenceByExternalIdsAndStorefront(
        Set<String> productExternalIds,
        Long storefrontId
    );
}
