package com.promox.core.category.internal.service;

import com.promox.core.category.CategoryDto;
import com.promox.core.category.CategoryExternalIdsExistenceResult;
import com.promox.core.category.CategoryIdsExistenceResult;
import com.promox.core.category.CategoryLookupApi;
import com.promox.core.category.internal.CategoryMapper;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;


@Service
public class CategoryLookupApiImpl implements CategoryLookupApi {
    private final CategoryService categoryService;

    public CategoryLookupApiImpl(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    public Optional<CategoryDto> findById(Long categoryId) {
        return categoryService.fetchByIdWithParentCategory(categoryId)
                .map(CategoryMapper::toDto);
    }

    public CategoryIdsExistenceResult checkExistenceByIdsAndStorefront(Set<Long> categoryIds, Long storefrontId) {
        return categoryService.checkExistenceByIdsAndStorefront(categoryIds, storefrontId);
    }

    public CategoryExternalIdsExistenceResult checkExistenceByExternalIdsAndStorefront(
        Set<String> categoryExternalIds,
        Long storefrontId
    ) {
        return categoryService.checkExistenceByExternalIdsAndStorefront(categoryExternalIds, storefrontId);
    }

    public List<CategoryDto> getListById(Collection<Long> categoryIds, Long storefrontId) {
        return categoryService.getListById(categoryIds, storefrontId).stream()
            .map(CategoryMapper::toDto)
            .toList();
    }
}
