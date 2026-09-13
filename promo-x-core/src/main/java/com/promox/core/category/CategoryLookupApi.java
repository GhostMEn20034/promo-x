package com.promox.core.category;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface CategoryLookupApi {
    Optional<CategoryDto> findById(Long categoryId);
    CategoryIdsExistenceResult checkExistenceByIdsAndStorefront(Set<Long> categoryIds, Long storefrontId);
    List<CategoryDto> getListById(Collection<Long> categoryIds, Long storefrontId);
}
