package com.promox.core.category;

import com.promox.core.category.internal.CategoryMapper;
import com.promox.core.category.internal.service.CategoryService;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;


@Service
public class CategoryLookupApi {
    private final CategoryService categoryService;

    public CategoryLookupApi(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    public Optional<CategoryDto> findById(Long categoryId) {
        return categoryService.fetchByIdWithParentCategory(categoryId)
                .map(CategoryMapper::toDto);
    }

    public CategoryIdsExistenceResult checkExistenceByIdsAndStorefront(Set<Long> categoryIds, Long storefrontId) {
        return categoryService.checkExistenceByIdsAndStorefront(categoryIds, storefrontId);
    }
}
