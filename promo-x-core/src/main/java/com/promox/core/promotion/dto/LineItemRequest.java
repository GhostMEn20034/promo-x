package com.promox.core.promotion.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record LineItemRequest(
        @NotNull String productExternalId,
        @NotNull String productSku,
        List<String> categories,
        List<String> collections,
        @Min(value = 1) int quantity,
        @DecimalMin(value = "0.00", message = "Price per unit must be positive") BigDecimal unitPrice
) {}
