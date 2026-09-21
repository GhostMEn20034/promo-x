package com.promox.core.promotion.internal.dto;

import java.math.BigDecimal;
import java.util.List;

public record PromotionEvaluationResponse(
        String sessionId,
        String customerId,
        List<String> promoCodes,

        BigDecimal oldTotal,
        BigDecimal newTotal,
        BigDecimal totalDiscount,

        BigDecimal itemsSubtotal,
        BigDecimal itemsDiscount,
        BigDecimal itemsTotal,

        BigDecimal oldShippingCost,
        BigDecimal shippingDiscount,
        BigDecimal newShippingCost,

        List<LineItemResult> items,
        List<RuleResult> matchedRules,
        List<RuleResult> appliedRules
) {
    public record LineItemResult(
            String productExternalId,
            String productSku,
            List<String> categories,
            List<String> collections,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal priceBeforeDiscount,
            BigDecimal discountAmount,
            BigDecimal priceAfterDiscount
    ) {}

    public record RuleResult(
            Long ruleId,
            String ruleName
    ) {}
}
