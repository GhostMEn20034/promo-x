package com.promox.core.product.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateProductRequest(
    @Size(min = 1, max = 255)
    String externalId,
    @NotBlank
    @Size(max = 255)
    String sku,
    @NotBlank
    @Size(max = 255)
    String name,
    Set<@NotNull @Positive Long> categoryIds
) {
    public CreateProductRequest {
        categoryIds = (categoryIds == null) ? Set.of() : Set.copyOf(categoryIds);
    }
}
