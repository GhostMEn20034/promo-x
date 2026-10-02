package com.promox.core.promotion.internal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record PromotionEvaluationRequest(
        String sessionId, // Can be a Cart ID, Session Cookie, or Device UUID
        String customerId, // Nullable to natively support anonymous guest checkouts
        @NotNull List<String> promoCodes,
        @NotNull @Size(min=1, max = 128) List<@Valid PromotionEvaluationItem> items,
        @NotNull @DecimalMin(value = "0.00", message = "Shipping cost must be positive") BigDecimal shippingCost,
        // true (default): uses of limited rules are RESERVED until confirmed or canceled.
        // false: they are counted as USED right away, for callers that don't need reservations.
        Boolean reserveUsage
) {
    public PromotionEvaluationRequest {
        if (reserveUsage == null) {
            reserveUsage = true;
        }
    }
}
