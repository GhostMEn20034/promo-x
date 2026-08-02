package com.promox.core.category.internal.service;

import com.promox.core.category.dto.CreateCategoryRequest;
import com.promox.core.category.internal.exception.CategoryAlreadyExistsException;
import com.promox.core.category.internal.exception.ParentCategoryNotFoundException;
import com.promox.core.category.internal.persistence.Category;
import com.promox.core.category.internal.persistence.CategoryRepository;
import jakarta.annotation.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class CategoryService {
    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public Category createCategory(String storefrontId, CreateCategoryRequest creationRequest) {
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

    private void validateExternalIdUniqueness(String storefrontId, @Nullable String externalId) {
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

    @Nullable
    private Category fetchAndValidateParent(@Nullable Long parentId, String storefrontId) {
        if (parentId == null) {
            return null;
        }

        return categoryRepository.findByIdAndStorefrontId(parentId, storefrontId)
                .orElseThrow(() -> new ParentCategoryNotFoundException(parentId));
    }
}
