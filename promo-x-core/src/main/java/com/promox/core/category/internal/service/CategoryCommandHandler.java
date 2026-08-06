package com.promox.core.category.internal.service;

import com.promox.core.category.dto.CategoryDto;
import com.promox.core.category.dto.CreateCategoryRequest;
import com.promox.core.category.internal.CategoryMapper;
import com.promox.core.category.internal.exception.CategoryBusinessException;
import com.promox.core.storefront.StorefrontLookupApi;
import com.promox.core.storefront.StorefrontDto;
import org.springframework.stereotype.Service;

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
                    "The specified storefront does not exist."
            );
        }

        var category = categoryService.createCategory(storefrontId, request);
        return CategoryMapper.toDto(category);
    }
}
