package com.promox.core.promotion.engine;

import com.promox.core.promotion.internal.dto.LineItemRequest;
import com.promox.core.promotion.internal.dto.PromotionEvaluationParams;
import com.promox.core.promotion.internal.service.action.FreeShippingAction;
import com.promox.core.promotion.internal.service.action.ProductBuyXGetYFreeAction;
import com.promox.core.promotion.internal.service.action.ProductPercentageDiscountAction;
import com.promox.core.promotion.internal.service.action.ProductSKUPercentageDiscountAction;
import com.promox.core.promotion.internal.service.condition.Operator;
import com.promox.core.promotion.internal.service.condition.PromoCodeConditionNode;
import com.promox.core.promotion.internal.context.AppliedRuleLog;
import com.promox.core.promotion.internal.context.EngineWorkspace;
import com.promox.core.promotion.internal.context.MatchedRuleLog;
import com.promox.core.promotion.internal.service.engine.PromoRuleEngine;
import com.promox.core.promotion.internal.persistence.PromotionRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class PromoRuleEngineExecutionTest {

    private static final Long STOREFRONT_ID = 123L;
    private final PromoRuleEngine engine = new PromoRuleEngine();

    @Test
    @DisplayName("Should execute rules in descending priority order and short-circuit pipeline when high-priority rule applies")
    void calculate_ShouldExecuteInPriorityOrderAndHalt_WhenShortCircuitRuleApplies() {
        // Arrange: Workspace matching promo code "VIP" with a $100 line item
        PromotionEvaluationParams request = new PromotionEvaluationParams(
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
        EngineWorkspace workspace = new EngineWorkspace(STOREFRONT_ID, request);

        // High Priority Rule (Priority: 100, ShortCircuit: TRUE -> 10% OFF PROD-1)
        PromotionRule highPriorityRule = PromotionRule.builder()
            .id(1L)
            .storefrontId(STOREFRONT_ID)
            .name("High Priority VIP Rule")
            .priority(100)
            .isShortCircuit(true)
            .conditionTree(new PromoCodeConditionNode("VIP", Operator.EQUALS))
            .action(new ProductPercentageDiscountAction("PROD-1", new BigDecimal("0.10")))
            .build();

        // Low Priority Rule (Priority: 10, ShortCircuit: FALSE -> 20% OFF PROD-1)
        PromotionRule lowPriorityRule = PromotionRule.builder()
            .id(2L)
            .storefrontId(STOREFRONT_ID)
            .name("Low Priority VIP Rule")
            .priority(10)
            .isShortCircuit(false)
            .conditionTree(new PromoCodeConditionNode("VIP", Operator.EQUALS))
            .action(new ProductPercentageDiscountAction("PROD-1", new BigDecimal("0.20")))
            .build();

        // The engine assumes pre-sorted input, so sort here the way callers are expected to
        List<PromotionRule> sortedRules = List.of(lowPriorityRule, highPriorityRule).stream()
                .sorted(Comparator.comparingInt(PromotionRule::getPriority).reversed())
                .toList();

        engine.calculate(workspace, sortedRules);

        // Assert Engine Log Context
        assertThat(workspace.getMatchedRules())
                .extracting(MatchedRuleLog::ruleId)
                .containsExactly(1L);

        assertThat(workspace.getAppliedRules())
                .extracting(AppliedRuleLog::ruleId)
                .containsExactly(1L);

        // Assert calculated discount ($10.00 from 10% discount, ignoring low-priority 20%)
        assertThat(workspace.getItemsDiscount()).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    @DisplayName("Should sequentially apply multiple rules across a multi-item workspace")
    void calculate_ShouldApplyMultipleRulesAndActions_WhenWorkspaceContainsMultipleItems() {
        // Arrange: Cart with 2 Keyboards ($100 each) + 1 Mouse ($50) | Shipping: $15.00 | Promo Code: "SUMMER"
        PromotionEvaluationParams request = new PromotionEvaluationParams(
                "sess-1",
                "cust-1",
                List.of("SUMMER"),
                List.of(
                        new LineItemRequest("PROD-KB", "SKU-KB", List.of("Tech"), List.of("Default"), 2, new BigDecimal("100.00")),
                        new LineItemRequest("PROD-MS", "SKU-MS", List.of("Tech"), List.of("Default"), 1, new BigDecimal("50.00"))
                ),
                new BigDecimal("15.00")
        );
        EngineWorkspace workspace = new EngineWorkspace(STOREFRONT_ID, request);

        // Rule 1: 20% off Keyboards (Priority 200)
        PromotionRule keyboardRule = PromotionRule.builder()
            .id(1L)
            .storefrontId(STOREFRONT_ID)
            .name("Summer Tech Keyboard Discount")
            .priority(200)
            .isShortCircuit(false)
            .isActive(true)
            .conditionTree(new PromoCodeConditionNode("SUMMER", Operator.EQUALS))
            .action(new ProductPercentageDiscountAction("PROD-KB", new BigDecimal("0.20")))
            .build();

        // Rule 2: 10% off Mouse (Priority 150)
        PromotionRule mouseRule = PromotionRule.builder()
            .id(2L)
            .storefrontId(STOREFRONT_ID)
            .name("Summer Tech Mouse Discount")
            .priority(150)
            .isShortCircuit(false)
            .isActive(true)
            .conditionTree(new PromoCodeConditionNode("SUMMER", Operator.EQUALS))
            .action(new ProductSKUPercentageDiscountAction("SKU-MS", new BigDecimal("0.10")))
            .build();

        // Rule 3: Free Shipping (Priority 100)
        PromotionRule shippingRule = PromotionRule.builder()
            .id(3L)
            .storefrontId(STOREFRONT_ID)
            .name("Free Shipping Promotion")
            .priority(100)
            .isShortCircuit(false)
            .isActive(true)
            .conditionTree(new PromoCodeConditionNode("SUMMER", Operator.EQUALS))
            .action(new FreeShippingAction())
            .build();

        // The engine assumes pre-sorted input, so sort here the way callers are expected to
        List<PromotionRule> rules = List.of(shippingRule, mouseRule, keyboardRule).stream()
                .sorted(Comparator.comparingInt(PromotionRule::getPriority).reversed())
                .toList();

        engine.calculate(workspace, rules);

        // Assert Engine Execution Tracking (Executed in priority order: 200 -> 150 -> 100)
        assertThat(workspace.getMatchedRules())
                .extracting(MatchedRuleLog::ruleId)
                .containsExactly(1L, 2L, 3L);

        assertThat(workspace.getAppliedRules())
                .extracting(AppliedRuleLog::ruleId)
                .containsExactly(1L, 2L, 3L);

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
        PromotionEvaluationParams request = new PromotionEvaluationParams(
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
        EngineWorkspace workspace = new EngineWorkspace(STOREFRONT_ID, request);

        // Rule 1 (Priority 100, ShortCircuit: TRUE)
        // Condition matches promo code "MATCH_ONLY", BUT action requires Buy 2 Get 1 Free on PROD-1.
        // Since cart only has 1 unit, BuyXGetYFreeAction returns false.
        PromotionRule highPriorityInapplicableRule = PromotionRule.builder()
            .id(1L)
            .storefrontId(STOREFRONT_ID)
            .name("Inapplicable BOGO Rule")
            .priority(100)
            .isShortCircuit(true)
            .isActive(true)
            .conditionTree(new PromoCodeConditionNode("MATCH_ONLY", Operator.EQUALS))
            .action(new ProductBuyXGetYFreeAction("PROD-1", 2, 1))
            .build();

        // Rule 2 (Priority 50, ShortCircuit: FALSE)
        // Fallback rule: 15% OFF PROD-1.
        PromotionRule lowPriorityFallbackRule = PromotionRule.builder()
            .id(2L)
            .storefrontId(STOREFRONT_ID)
            .name("Fallback 15% Discount Rule")
            .priority(50)
            .isShortCircuit(false)
            .isActive(true)
            .conditionTree(new PromoCodeConditionNode("MATCH_ONLY", Operator.EQUALS))
            .action(new ProductPercentageDiscountAction("PROD-1", new BigDecimal("0.15")))
            .build();

        List<PromotionRule> rules = List.of(highPriorityInapplicableRule, lowPriorityFallbackRule);

        engine.calculate(workspace, rules);

        // Assert Engine Tracking:
        // Both rules evaluated true for conditions -> Both logged in matchedRules
        assertThat(workspace.getMatchedRules())
                .extracting(MatchedRuleLog::ruleId)
                .containsExactly(1L, 2L);

        // Only Rule 2 actually modified cart state -> Only Rule 2 logged in appliedRules
        assertThat(workspace.getAppliedRules())
                .extracting(AppliedRuleLog::ruleId)
                .containsExactly(2L);

        // Assert Discount Calculations:
        // 15% applied to $100 = $15.00 discount
        assertThat(workspace.getItemsDiscount()).isEqualByComparingTo(new BigDecimal("15.00"));
        assertThat(workspace.getGrandTotal()).isEqualByComparingTo(new BigDecimal("85.00"));
    }
}