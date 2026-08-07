package com.promox.core.category.internal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;


public interface CategoryRepository extends JpaRepository<Category, Long> {
    boolean existsByStorefrontIdAndExternalId(Long storefrontId, String externalId);

    Optional<Category> findByIdAndStorefrontId(Long id, Long storefrontId);

    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.parent WHERE c.id = ?1")
    Optional<Category> findByIdWithParent(Long categoryId);
}
