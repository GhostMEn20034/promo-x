package com.promox.core.promotion.engine;

import com.promox.core.promotion.dto.LineItemRequest;
import com.promox.core.promotion.dto.PromotionEvaluationRequest;
import com.promox.core.promotion.internal.action.FreeShippingAction;
import com.promox.core.promotion.internal.action.ProductIdBuyXGetYFreeAction;
import com.promox.core.promotion.internal.action.ProductIdPercentageDiscountAction;
import com.promox.core.promotion.internal.action.ProductSKUPercentageDiscountAction;
import com.promox.core.promotion.internal.condition.Operator;
import com.promox.core.promotion.internal.condition.PromoCodeConditionNode;
import com.promox.core.promotion.internal.context.AppliedRuleLog;
import com.promox.core.promotion.internal.context.EngineWorkspace;
import com.promox.core.promotion.internal.context.MatchedRuleLog;
import com.promox.core.promotion.internal.engine.PromoRuleEngine;
import com.promox.core.promotion.internal.engine.PromotionRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class PromoRuleEngineExecutionTest {
    private final PromoRuleEngine engine = new PromoRuleEngine();

    @Test
    @DisplayName("Should execute rules in descending priority order and short-circuit pipeline when high-priority rule applies")
    void calculate_ShouldExecuteInPriorityOrderAndHalt_WhenShortCircuitRuleApplies() {
        // Arrange: Workspace matching promo code "VIP" with a $100 line item
        PromotionEvaluationRequest request = new PromotionEvaluationRequest(
                "us-storefront",
                "sess-1",
                "cust-1",
                List.of("VIP"),
                List.of(new LineItemRequest(
                        "PROD-1",
                        "SKU-1",
                        List.of("Electronics"),
                        List.of("Default"),
                        1,
                        new BigDecimal("100.00")
                )),
                BigDecimal.ZERO
        );
        EngineWorkspace workspace = new EngineWorkspace(request);

        // High Priority Rule (Priority: 100, ShortCircuit: TRUE -> 10% OFF PROD-1)
        PromotionRule highPriorityRule = new PromotionRule(
                "RULE-HIGH-PRIORITY",
                "us-storefront",
                "High Priority VIP Rule",
                100,
                true,
                new PromoCodeConditionNode("VIP", Operator.EQUALS),
                List.of(new ProductIdPercentageDiscountAction("PROD-1", new BigDecimal("0.10")))
        );

        // Low Priority Rule (Priority: 10, ShortCircuit: FALSE -> 20% OFF PROD-1)
        PromotionRule lowPriorityRule = new PromotionRule(
                "RULE-LOW-PRIORITY",
                "us-storefront",
                "Low Priority VIP Rule",
                10,
                false,
                new PromoCodeConditionNode("VIP", Operator.EQUALS),
                List.of(new ProductIdPercentageDiscountAction("PROD-1", new BigDecimal("0.20")))
        );

        // Pass rules out of priority order to verify internal engine sorting
        List<PromotionRule> unsortedRules = List.of(lowPriorityRule, highPriorityRule);

        engine.calculate(workspace, unsortedRules);

        // Assert Engine Log Context
        assertThat(workspace.getMatchedRules())
                .extracting(MatchedRuleLog::ruleId)
                .containsExactly("RULE-HIGH-PRIORITY");

        assertThat(workspace.getAppliedRules())
                .extracting(AppliedRuleLog::ruleId)
                .containsExactly("RULE-HIGH-PRIORITY");

        // Assert calculated discount ($10.00 from 10% discount, ignoring low-priority 20%)
        assertThat(workspace.getItemsDiscount()).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    @DisplayName("Should sequentially apply multiple rules with multiple actions across a multi-item workspace")
    void calculate_ShouldApplyMultipleRulesAndActions_WhenWorkspaceContainsMultipleItems() {
        // Arrange: Cart with 2 Keyboards ($100 each) + 1 Mouse ($50) | Shipping: $15.00 | Promo Code: "SUMMER"
        PromotionEvaluationRequest request = new PromotionEvaluationRequest(
                "us-storefront",
                "sess-1",
                "cust-1",
                List.of("SUMMER"),
                List.of(
                        new LineItemRequest("PROD-KB", "SKU-KB", List.of("Tech"), List.of("Default"), 2, new BigDecimal("100.00")),
                        new LineItemRequest("PROD-MS", "SKU-MS", List.of("Tech"), List.of("Default"), 1, new BigDecimal("50.00"))
                ),
                new BigDecimal("15.00")
        );
        EngineWorkspace workspace = new EngineWorkspace(request);

        // Rule 1 (Priority 200, ShortCircuit: FALSE)
        // Applies 2 actions: 20% off Keyboards + 10% off Mouse
        PromotionRule multiActionTechRule = new PromotionRule(
                "RULE-SUMMER-TECH",
                "us-storefront",
                "Summer Tech Multi-Item Bundle",
                200,
                false,
                new PromoCodeConditionNode("SUMMER", Operator.EQUALS),
                List.of(
                        new ProductIdPercentageDiscountAction("PROD-KB", new BigDecimal("0.20")),
                        new ProductSKUPercentageDiscountAction("SKU-MS", new BigDecimal("0.10"))
                )
        );

        // Rule 2 (Priority 100, ShortCircuit: FALSE)
        // Applies 1 action: Free Shipping
        PromotionRule shippingRule = new PromotionRule(
                "RULE-FREE-SHIPPING",
                "us-storefront",
                "Free Shipping Promotion",
                100,
                false,
                new PromoCodeConditionNode("SUMMER", Operator.EQUALS),
                List.of(new FreeShippingAction())
        );

        List<PromotionRule> rules = List.of(shippingRule, multiActionTechRule);

        engine.calculate(workspace, rules);

        // Assert Engine Execution Tracking
        assertThat(workspace.getMatchedRules())
                .extracting(MatchedRuleLog::ruleId)
                .containsExactly("RULE-SUMMER-TECH", "RULE-FREE-SHIPPING");

        assertThat(workspace.getAppliedRules())
                .extracting(AppliedRuleLog::ruleId)
                .containsExactly("RULE-SUMMER-TECH", "RULE-FREE-SHIPPING");

        // Assert Workspace Totals & Discounts:
        // - KB Discount: 2 * $100 * 20% = $40.00
        // - MS Discount: 1 * $50 * 10%  = $5.00
        // - Items Discount Total       = $45.00
        // - Shipping Discount Total    = $15.00
        assertThat(workspace.getItemsDiscount()).isEqualByComparingTo(new BigDecimal("45.00"));
        assertThat(workspace.getShippingCostDiscount()).isEqualByComparingTo(new BigDecimal("15.00"));
        assertThat(workspace.getTotalDiscount()).isEqualByComparingTo(new BigDecimal("60.00"));

        // Grand Total: ($250 items subtotal - $45 items discount) + ($15 shipping - $15 shipping discount) = $205.00
        assertThat(workspace.getGrandTotal()).isEqualByComparingTo(new BigDecimal("205.00"));
    }

    @Test
    @DisplayName("Should log matched rule without applying it when action fails, allowing lower-priority rule to execute")
    void calculate_ShouldNotShortCircuitOrLogApplied_WhenRuleIsMatchedButActionReturnsFalse() {
        // Arrange: Cart with 1 unit of PROD-1 ($100) and Promo Code "MATCH_ONLY"
        PromotionEvaluationRequest request = new PromotionEvaluationRequest(
                "us-storefront",
                "sess-1",
                "cust-1",
                List.of("MATCH_ONLY"),
                List.of(new LineItemRequest(
                        "PROD-1",
                        "SKU-1",
                        List.of("Electronics"),
                        List.of("Default"),
                        1,
                        new BigDecimal("100.00")
                )),
                BigDecimal.ZERO
        );
        EngineWorkspace workspace = new EngineWorkspace(request);

        // Rule 1 (Priority 100, ShortCircuit: TRUE)
        // Condition matches promo code "MATCH_ONLY", BUT action requires Buy 2 Get 1 Free on PROD-1.
        // Since cart only has 1 unit, BuyXGetYFreeAction returns false.
        PromotionRule highPriorityInapplicableRule = new PromotionRule(
                "RULE-INAPPLICABLE-BOGO",
                "us-storefront",
                "Inapplicable BOGO Rule",
                100,
                true, // Short circuit enabled, but should NOT fire because action won't apply
                new PromoCodeConditionNode("MATCH_ONLY", Operator.EQUALS),
                List.of(new ProductIdBuyXGetYFreeAction("PROD-1", 2, 1))
        );

        // Rule 2 (Priority 50, ShortCircuit: FALSE)
        // Fallback rule: 15% OFF PROD-1.
        PromotionRule lowPriorityFallbackRule = new PromotionRule(
                "RULE-FALLBACK-PERCENTAGE",
                "us-storefront",
                "Fallback 15% Discount Rule",
                50,
                false,
                new PromoCodeConditionNode("MATCH_ONLY", Operator.EQUALS),
                List.of(new ProductIdPercentageDiscountAction("PROD-1", new BigDecimal("0.15")))
        );

        List<PromotionRule> rules = List.of(highPriorityInapplicableRule, lowPriorityFallbackRule);

        engine.calculate(workspace, rules);

        // Assert Engine Tracking:
        // Both rules evaluated true for conditions -> Both logged in matchedRules
        assertThat(workspace.getMatchedRules())
                .extracting(MatchedRuleLog::ruleId)
                .containsExactly("RULE-INAPPLICABLE-BOGO", "RULE-FALLBACK-PERCENTAGE");

        // Only Rule 2 actually modified cart state -> Only Rule 2 logged in appliedRules
        assertThat(workspace.getAppliedRules())
                .extracting(AppliedRuleLog::ruleId)
                .containsExactly("RULE-FALLBACK-PERCENTAGE");

        // Assert Discount Calculations:
        // 15% applied to $100 = $15.00 discount
        assertThat(workspace.getItemsDiscount()).isEqualByComparingTo(new BigDecimal("15.00"));
        assertThat(workspace.getGrandTotal()).isEqualByComparingTo(new BigDecimal("85.00"));
    }
}
