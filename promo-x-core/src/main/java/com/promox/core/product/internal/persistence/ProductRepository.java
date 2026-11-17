package com.promox.core.product.internal.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ProductRepository extends JpaRepository<Product, Long>, ProductRepositoryCustom {
    @EntityGraph(attributePaths = "categoryIds")
    Optional<Product> findWithCategoryById(Long id);

    // No entity graph: fetching the categoryIds collection with a page would make Hibernate page in memory
    Page<Product> findAllByStorefrontId(Long storefrontId, Pageable pageable);

    @EntityGraph(attributePaths = "categoryIds")
    List<Product> findAllWithCategoryIdsByIdIn(Collection<Long> ids);

    boolean existsByExternalIdAndStorefrontId(String externalId, Long storefrontId);

    @Query("SELECT p.externalId FROM Product p WHERE p.externalId IN :externalIds AND p.storefrontId = :storefrontId")
    Set<String> findExistingExternalIds(
        @Param("externalIds") Set<String> externalIds,
        @Param("storefrontId") Long storefrontId
    );

    Long countByStorefrontId(Long storefrontId);

    Optional<Product> findByExternalId(String externalId);

    @EntityGraph(attributePaths = "categoryIds")
    List<Product> findAllByStorefrontIdAndExternalIdIn(Long storefrontId, Collection<String> externalIds);

    @Query("SELECT p.externalId FROM Product p JOIN p.categoryIds c WHERE p.storefrontId = :storefrontId AND c = :categoryId")
    List<String> findExternalIdsByStorefrontIdAndCategoryId(
        @Param("storefrontId") Long storefrontId,
        @Param("categoryId") Long categoryId
    );
}
