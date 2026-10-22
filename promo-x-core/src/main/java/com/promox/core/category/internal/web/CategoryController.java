package com.promox.core.category.internal.web;

import com.promox.core.category.CategoryLookupApi;
import com.promox.core.category.internal.dto.UpdateCategoryRequest;
import com.promox.core.category.internal.exception.CategoryNotFoundException;
import com.promox.core.category.internal.service.CategoryCommandHandler;
import com.promox.core.category.CategoryDto;
import com.promox.core.category.internal.dto.CreateCategoryRequest;
import com.promox.core.infrastructure.security.Roles;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/category")
@PreAuthorize("hasRole('" + Roles.EMPLOYEE + "')")
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
    @PreAuthorize("hasAnyRole('" + Roles.EMPLOYEE + "', '" + Roles.AUDITOR + "')")
    public CategoryDto getById(@PathVariable Long categoryId) {
        return categoryLookupApi.findById(categoryId)
                .orElseThrow(() ->
                        new CategoryNotFoundException(categoryId)
                );
    }

    @PutMapping("/{categoryId}")
    public CategoryDto updateCategoryById(
            @PathVariable Long categoryId,
            @Valid @RequestBody UpdateCategoryRequest requestBody
    ) {
        return categoryCommandHandler.updateCategoryById(categoryId, requestBody)
                .orElseThrow(() ->
                        new CategoryNotFoundException(categoryId)
                );
    }
}
