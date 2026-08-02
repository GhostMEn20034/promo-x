package com.promox.core.category.internal.service;

import com.promox.core.category.dto.CategoryDto;
import com.promox.core.category.dto.CreateCategoryRequest;
import com.promox.core.category.dto.ParentCategoryDto;
import org.springframework.stereotype.Service;

@Service
public class CategoryCommandHandler {
    private final CategoryService categoryService;

    public CategoryCommandHandler(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    public CategoryDto createSingleCategory(String storefrontId, CreateCategoryRequest request) {
        var category = categoryService.createCategory(storefrontId, request);
        return new CategoryDto(
                category.getId(),
                category.getExternalId(),
                category.getName(),
                category.getParentId(),
                category.getParent() != null ? new ParentCategoryDto(
                        category.getParent().getId(),
                        category.getParent().getExternalId(),
                        category.getParent().getName(),
                        category.getParent().getParentId(),
                        category.getParent().getStorefrontId()
                ) : null,
                category.getStorefrontId()
        );
    }
}
