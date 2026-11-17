package com.promox.core.category.internal.persistence;

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


public interface CategoryRepository extends JpaRepository<Category, Long> {
    boolean existsByStorefrontIdAndExternalId(Long storefrontId, String externalId);

    Optional<Category> findByIdAndStorefrontId(Long id, Long storefrontId);

    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.parent WHERE c.id = ?1")
    Optional<Category> findByIdWithParent(Long categoryId);

    @Query("SELECT c.id FROM Category c WHERE c.id IN :ids AND c.storefrontId = :storefrontId")
    Set<Long> findExistingIds(@Param("ids") Set<Long> ids, @Param("storefrontId") Long storefrontId);

    @Query("SELECT c.externalId FROM Category c WHERE c.externalId IN :externalIds AND c.storefrontId = :storefrontId")
    Set<String> findExistingExternalIds(
        @Param("externalIds") Set<String> externalIds,
        @Param("storefrontId") Long storefrontId
    );

    @Query("SELECT c.id FROM Category c WHERE c.id IN :ids")
    Set<Long> findExistingIds(@Param("ids") Set<Long> ids);

    @EntityGraph(attributePaths = {"parent"})
    List<Category> findAllByIdInAndStorefrontId(Collection<Long> ids, Long storefrontId);

    // The parent is a to-one association, so fetching it with the page is a join and doesn't page in memory
    @EntityGraph(attributePaths = {"parent"})
    Page<Category> findAllByStorefrontId(Long storefrontId, Pageable pageable);
}
