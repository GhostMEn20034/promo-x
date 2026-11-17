package com.promox.core.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ProductLookupApi {
    Optional<ProductWithCategoriesDto> findWithCategoriesById(Long productId);

    List<DefaultProductDto> findAllByExternalIds(Set<String> externalIds, Long storefrontId);

    List<ProductWithCategoriesDto> findAllWithCategoriesByExternalIds(Set<String> externalIds, Long storefrontId);

    Page<ProductWithCategoriesDto> findAllWithCategories(Long storefrontId, Pageable pageable);

    ProductExternalIdsExistenceResult checkExistenceByExternalIdsAndStorefront(
        Set<String> productExternalIds,
        Long storefrontId
    );
}
