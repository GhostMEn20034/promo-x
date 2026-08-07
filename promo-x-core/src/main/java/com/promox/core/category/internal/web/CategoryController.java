package com.promox.core.category.internal.web;

import com.promox.core.category.CategoryLookupApi;
import com.promox.core.category.internal.exception.CategoryNotFoundException;
import com.promox.core.category.internal.service.CategoryCommandHandler;
import com.promox.core.category.dto.CategoryDto;
import com.promox.core.category.dto.CreateCategoryRequest;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/category")
public class CategoryController {
    private final CategoryCommandHandler categoryCommandHandler;
    private final CategoryLookupApi categoryLookupApi;

    CategoryController(CategoryCommandHandler categoryCommandHandler, CategoryLookupApi categoryLookupApi) {
        this.categoryCommandHandler = categoryCommandHandler;
        this.categoryLookupApi = categoryLookupApi;
    }

    @PostMapping("/")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryDto createSingleCategory(
            @RequestHeader("X-Storefront-Id") Long storefrontId,
            @Valid @RequestBody CreateCategoryRequest requestBody
    ) {
        return categoryCommandHandler.createSingleCategory(storefrontId, requestBody);
    }

    @GetMapping("/{categoryId}")
    public CategoryDto getById(@PathVariable Long categoryId) {
        return categoryLookupApi.findById(categoryId)
                .orElseThrow(() ->
                        new CategoryNotFoundException(categoryId)
                );
    }
}
