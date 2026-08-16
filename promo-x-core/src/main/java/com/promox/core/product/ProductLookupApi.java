package com.promox.core.product;

import java.util.Optional;

public interface ProductLookupApi {
    Optional<ProductWithCategoriesDto> findWithCategoriesById(Long productId);
}
