package com.promox.core.promotion.internal.service.condition;

public enum SubtotalType {
    /**
     * The original, face-value price of the items (Unit Price * Quantity).
     * Represents the "Worth" or "Gross" price of the cart before any discounts.
     */
    GROSS,

    /**
     * The current running price of the items after previous discounts have been applied.
     * Represents the actual "Net" price the customer is paying at this moment.
     */
    NET
}
