package com.promox.core.product.internal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;


public record CreateMultipleProductsRequest(
    @NotEmpty
    @Size(max = 1000)
    List<@Valid CreateProductRequest> items
) {}
