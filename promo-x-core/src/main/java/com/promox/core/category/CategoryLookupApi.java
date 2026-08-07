package com.promox.core.category;

import com.promox.core.category.internal.CategoryMapper;
import com.promox.core.category.internal.service.CategoryService;
import org.springframework.stereotype.Service;

import java.util.Optional;


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
}
