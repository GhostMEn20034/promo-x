package com.promox.core.category.internal.service;

import com.promox.core.category.CategoryIdsExistenceResult;
import com.promox.core.category.internal.dto.CreateCategoryRequest;
import com.promox.core.category.internal.dto.UpdateCategoryRequest;
import com.promox.core.category.internal.exception.CategoryAlreadyExistsException;
import com.promox.core.category.internal.exception.ParentCategoryNotFoundException;
import com.promox.core.category.internal.persistence.Category;
import com.promox.core.category.internal.persistence.CategoryRepository;
import jakarta.annotation.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;


@Service
public class CategoryService {
    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public Category createCategory(Long storefrontId, CreateCategoryRequest creationRequest) {
        validateExternalIdUniqueness(storefrontId, creationRequest.externalId());

        Category parent = fetchAndValidateParent(creationRequest.parentId(), storefrontId);

        var category = Category.builder()
                .name(creationRequest.name())
                .externalId(creationRequest.externalId())
                .storefrontId(storefrontId)
                .build();

        category.setParent(parent);

        return categoryRepository.save(category);
    }



    @Nullable
    private Category fetchAndValidateParent(@Nullable Long parentId, Long storefrontId) {
        if (parentId == null) {
            return null;
        }

        return categoryRepository.findByIdAndStorefrontId(parentId, storefrontId)
                .orElseThrow(() -> new ParentCategoryNotFoundException(parentId));
    }

    public Optional<Category> fetchById(Long categoryId) {
        return categoryRepository.findById(categoryId);
    }

    public Optional<Category> fetchByIdWithParentCategory(Long categoryId) {
        return categoryRepository.findByIdWithParent(categoryId);
    }

    public List<Category> getListById(Collection<Long> categoryIds, Long storefrontId) {
        return categoryRepository.findAllByIdInAndStorefrontId(categoryIds, storefrontId);
    }

    @Transactional(readOnly = true)
    public CategoryIdsExistenceResult checkExistenceByIdsAndStorefront(Set<Long> categoryIds, Long storefrontId) {
        if (categoryIds.isEmpty()) {
            return new CategoryIdsExistenceResult(Set.of(), Set.of());
        }

        Set<Long> existingIds = categoryRepository.findExistingIds(categoryIds, storefrontId);
        // Derive missing IDs via set difference
        Set<Long> missingIds = new HashSet<>(categoryIds);
        missingIds.removeAll(existingIds);

        return new CategoryIdsExistenceResult(existingIds, missingIds);
    }

    @Transactional
    public void updateCategory(Category categoryToUpdate, UpdateCategoryRequest updateRequest) {
        validateExternalIdUniqueness(categoryToUpdate.getStorefrontId(), updateRequest.externalId());

        Category parent = fetchAndValidateParent(updateRequest.parentId(), categoryToUpdate.getStorefrontId());

        categoryToUpdate.setName(updateRequest.name());
        categoryToUpdate.setExternalId(updateRequest.externalId());
        categoryToUpdate.setParent(parent);
    }

    private void validateExternalIdUniqueness(Long storefrontId, @Nullable String externalId) {
        if (externalId == null) {
            return;
        }

        boolean externalIdExists = categoryRepository.existsByStorefrontIdAndExternalId(storefrontId, externalId);
        if (externalIdExists) {
            throw new CategoryAlreadyExistsException(
                "Category with external ID '%s' already exists for this storefront."
                    .formatted(externalId),
                "externalId"
            );
        }
    }
}
