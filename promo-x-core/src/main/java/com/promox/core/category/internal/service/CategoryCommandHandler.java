package com.promox.core.category.internal.service;

import com.promox.core.category.CategoryDto;
import com.promox.core.category.internal.dto.CreateCategoryRequest;
import com.promox.core.category.internal.dto.UpdateCategoryRequest;
import com.promox.core.category.internal.CategoryMapper;
import com.promox.core.category.internal.exception.CategoryBusinessException;
import com.promox.core.storefront.StorefrontLookupApi;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CategoryCommandHandler {
    private final StorefrontLookupApi storefrontLookupApi;
    private final CategoryService categoryService;

    public CategoryCommandHandler(StorefrontLookupApi storefrontLookupApi, CategoryService categoryService) {
        this.storefrontLookupApi = storefrontLookupApi;
        this.categoryService = categoryService;
    }

    public CategoryDto createSingleCategory(Long storefrontId, CreateCategoryRequest request) {
        if (!storefrontLookupApi.existsById(storefrontId)) {
            throw CategoryBusinessException.of(
                    "Storefront does not exist.",
                    "INVALID_STOREFRONT",
                    "storefrontId",
                    "The specified storefront does not exist.",
                    null
            );
        }

        var category = categoryService.createCategory(storefrontId, request);
        return CategoryMapper.toDto(category);
    }

    public Optional<CategoryDto> updateCategoryById(
            Long categoryId,
            UpdateCategoryRequest updateRequest
    ) {
        return categoryService.fetchByIdWithParentCategory(categoryId)
                .map(category -> {
                    categoryService.updateCategory(category, updateRequest);
                    return CategoryMapper.toDto(category);
                });
    }
}
