package com.promox.core.category.internal.web;

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

    CategoryController(CategoryCommandHandler categoryCommandHandler) {
        this.categoryCommandHandler = categoryCommandHandler;
    }

    @PostMapping("/")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryDto createSingleCategory(@Valid @RequestBody CreateCategoryRequest requestBody) {
        return categoryCommandHandler.createSingleCategory("123", requestBody);
    }
}
