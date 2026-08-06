package com.promox.core.category.internal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;


public interface CategoryRepository extends JpaRepository<Category, Long> {
    boolean existsByStorefrontIdAndExternalId(Long storefrontId, String externalId);

    Optional<Category> findByIdAndStorefrontId(Long id, Long storefrontId);
}
