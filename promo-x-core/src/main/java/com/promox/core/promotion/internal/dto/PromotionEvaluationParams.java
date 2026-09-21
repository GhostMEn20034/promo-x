package com.promox.core.promotion.internal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;


/**
 * Public DTO encapsulating the contextual payload required to process promotions.
 * Supports anonymous users by leaving customerId nullable.
 */
public record PromotionEvaluationParams(
        String sessionId, // Can be a Cart ID, Session Cookie, or Device UUID
        String customerId, // Nullable to natively support anonymous guest checkouts
        @NotNull List<String> promoCodes,
        @Size(min=1) List<@Valid LineItemRequest> items,
        @DecimalMin(value = "0.00", message = "Shipping cost must be positive") BigDecimal shippingCost
) {}