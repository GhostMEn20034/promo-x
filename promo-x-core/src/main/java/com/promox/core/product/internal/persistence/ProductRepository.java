package com.promox.core.product.internal.persistence;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    @EntityGraph(attributePaths = "categoryIds")
    Optional<Product> findWithCategoryById(Long id);

    boolean existsByExternalIdAndStorefrontId(String externalId, Long storefrontId);
}
