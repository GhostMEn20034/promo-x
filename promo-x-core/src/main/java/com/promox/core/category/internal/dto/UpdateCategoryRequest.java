package com.promox.core.category.internal.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


public record UpdateCategoryRequest(
        @NotBlank(message = "Category name's required")
        @Size(min = 2, max = 50)
        String name,

        @Size(min = 1, max = 512)
        String externalId,

        Long parentId
) {}
