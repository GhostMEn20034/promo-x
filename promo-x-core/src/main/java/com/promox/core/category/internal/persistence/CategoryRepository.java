package com.promox.core.category.internal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Set;


public interface CategoryRepository extends JpaRepository<Category, Long> {
    boolean existsByStorefrontIdAndExternalId(Long storefrontId, String externalId);

    Optional<Category> findByIdAndStorefrontId(Long id, Long storefrontId);

    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.parent WHERE c.id = ?1")
    Optional<Category> findByIdWithParent(Long categoryId);

    @Query("SELECT c.id FROM Category c WHERE c.id IN :ids AND c.storefrontId = :storefrontId")
    Set<Long> findExistingIds(@Param("ids") Set<Long> ids, @Param("storefrontId") Long storefrontId);

    @Query("SELECT c.id FROM Category c WHERE c.id IN :ids")
    Set<Long> findExistingIds(@Param("ids") Set<Long> ids);
}
