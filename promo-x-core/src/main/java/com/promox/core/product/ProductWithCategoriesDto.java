package com.promox.core.product;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.promox.core.category.CategoryDto;

import java.util.Set;

public record ProductWithCategoriesDto(
    @JsonUnwrapped ProductBaseDto base,
    Set<CategoryDto> categories
){}
