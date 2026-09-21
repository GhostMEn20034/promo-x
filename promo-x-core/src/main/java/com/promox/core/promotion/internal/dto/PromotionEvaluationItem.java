package com.promox.core.promotion.internal.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record PromotionEvaluationItem(
        @NotNull String productExternalId,
        @Min(value = 1) int quantity,
        @NotNull @DecimalMin(value = "0.00", message = "Price per unit must be positive") BigDecimal unitPrice
) {}
