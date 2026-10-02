package com.promox.core.promotion.engine;

import com.promox.core.promotion.internal.context.AppliedRuleLog;
import com.promox.core.promotion.internal.context.EngineWorkspace;
import com.promox.core.promotion.internal.context.MatchedRuleLog;
import com.promox.core.promotion.internal.dto.LineItemRequest;
import com.promox.core.promotion.internal.dto.PromotionEvaluationParams;
import com.promox.core.promotion.internal.persistence.PromotionRule;
import com.promox.core.promotion.internal.persistence.PromotionRuleBaseProjection;
import com.promox.core.promotion.internal.service.action.ActionStrategy;
import com.promox.core.promotion.internal.service.action.ProductBuyXGetYFreeAction;
import com.promox.core.promotion.internal.service.action.ProductPercentageDiscountAction;
import com.promox.core.promotion.internal.service.condition.Operator;
import com.promox.core.promotion.internal.service.condition.PromoCodeConditionNode;
import com.promox.core.promotion.internal.service.engine.PromoRuleEngine;
import com.promox.core.promotion.internal.service.engine.RuleUsageLimiter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class PromoRuleEngineUsageLimitTest {
    private static final Long STOREFRONT_ID = 123L;
    private static final String PRODUCT = "PROD-1";

    private final FakeUsageLimiter limiter = new FakeUsageLimiter();
    private final PromoRuleEngine engine = new PromoRuleEngine(limiter);

    @Test
    @DisplayName("Should take one use and apply a limited rule that still has uses left")
    void calculate_ShouldAcquireUseAndApply_WhenLimitedRuleHasUsesLeft() {
        EngineWorkspace workspace = workspaceWithOneUnit();
        PromotionRuleBaseProjection rule = rule(1L, 100, false, true, tenPercentOff());

        engine.calculate(workspace, List.of(rule));

        assertThat(limiter.acquired).containsExactly(1L);
        assertThat(limiter.released).isEmpty();
        assertThat(workspace.getAppliedRules()).containsExactly(new AppliedRuleLog(1L, "Rule 1", true));
        assertThat(workspace.getUsageLimitedAppliedRuleIds()).containsExactly(1L);
        assertThat(workspace.getItemsDiscount()).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("Should match but not apply a limited rule that has no uses left, and continue with the next rule")
    void calculate_ShouldSkipExhaustedRuleAndContinue_WhenLimitedRuleHasNoUsesLeft() {
        EngineWorkspace workspace = workspaceWithOneUnit();
        limiter.exhausted.add(1L);
        // Short-circuit must not trigger for a rule that was not applied.
        PromotionRuleBaseProjection exhausted = rule(1L, 100, true, true, tenPercentOff());
        PromotionRuleBaseProjection fallback = rule(
            2L, 50, false, false, new ProductPercentageDiscountAction(PRODUCT, new BigDecimal("0.20"))
        );

        engine.calculate(workspace, List.of(exhausted, fallback));

        assertThat(workspace.getMatchedRules()).extracting(MatchedRuleLog::ruleId).containsExactly(1L, 2L);
        assertThat(workspace.getAppliedRules()).containsExactly(new AppliedRuleLog(2L, "Rule 2", false));
        assertThat(workspace.getUsageLimitedAppliedRuleIds()).isEmpty();
        assertThat(limiter.released).isEmpty();
        assertThat(workspace.getItemsDiscount()).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("Should give the use back when a limited rule matches but its action changes nothing")
    void calculate_ShouldReleaseUse_WhenLimitedRuleActionIsNotApplied() {
        EngineWorkspace workspace = workspaceWithOneUnit();
        // Buy 2 get 1 free can't apply to a single unit.
        PromotionRuleBaseProjection rule = rule(1L, 100, false, true, new ProductBuyXGetYFreeAction(PRODUCT, 2, 1));

        engine.calculate(workspace, List.of(rule));

        assertThat(limiter.acquired).containsExactly(1L);
        assertThat(limiter.released).containsExactly(1L);
        assertThat(workspace.getMatchedRules()).extracting(MatchedRuleLog::ruleId).containsExactly(1L);
        assertThat(workspace.getAppliedRules()).isEmpty();
    }

    @Test
    @DisplayName("Should never consult the limiter for a rule without a usage limit")
    void calculate_ShouldNotUseLimiter_WhenRuleHasNoUsageLimit() {
        EngineWorkspace workspace = workspaceWithOneUnit();
        // Would block the rule if the limiter were asked.
        limiter.exhausted.add(1L);
        PromotionRuleBaseProjection rule = rule(1L, 100, false, false, tenPercentOff());

        engine.calculate(workspace, List.of(rule));

        assertThat(limiter.acquired).isEmpty();
        assertThat(workspace.getAppliedRules()).containsExactly(new AppliedRuleLog(1L, "Rule 1", false));
        assertThat(workspace.getUsageLimitedAppliedRuleIds()).isEmpty();
    }

    @Test
    @DisplayName("Should report only the limited rules among the applied ones as holding a use")
    void calculate_ShouldTrackLimitedAppliedRules_WhenLimitedAndUnlimitedRulesApply() {
        EngineWorkspace workspace = workspaceWithOneUnit();
        PromotionRuleBaseProjection limited = rule(1L, 100, false, true, tenPercentOff());
        PromotionRuleBaseProjection unlimited = rule(2L, 50, false, false, tenPercentOff());

        engine.calculate(workspace, List.of(limited, unlimited));

        assertThat(workspace.getAppliedRules()).extracting(AppliedRuleLog::ruleId).containsExactly(1L, 2L);
        assertThat(workspace.getUsageLimitedAppliedRuleIds()).containsExactly(1L);
        assertThat(limiter.acquired).containsExactly(1L);
    }

    private static ActionStrategy tenPercentOff() {
        return new ProductPercentageDiscountAction(PRODUCT, new BigDecimal("0.10"));
    }

    private static PromotionRuleBaseProjection rule(
        Long id,
        int priority,
        boolean shortCircuit,
        boolean hasUsageLimit,
        ActionStrategy action
    ) {
        return PromotionRuleBaseProjection.from(
            PromotionRule.builder()
                .id(id)
                .storefrontId(STOREFRONT_ID)
                .name("Rule " + id)
                .priority(priority)
                .isShortCircuit(shortCircuit)
                .isActive(true)
                .hasUsageLimit(hasUsageLimit)
                .conditionTree(new PromoCodeConditionNode("VIP", Operator.EQUALS))
                .action(action)
                .build()
        );
    }

    /** One unit of PROD-1 at 100.00 with promo code VIP. */
    private static EngineWorkspace workspaceWithOneUnit() {
        return new EngineWorkspace(STOREFRONT_ID, new PromotionEvaluationParams(
            "sess-1",
            "cust-1",
            List.of("VIP"),
            List.of(new LineItemRequest(
                PRODUCT, "SKU-1", List.of("Electronics"), List.of("Default"), 1, new BigDecimal("100.00")
            )),
            BigDecimal.ZERO
        ));
    }

    private static final class FakeUsageLimiter implements RuleUsageLimiter {
        private final Set<Long> exhausted = new HashSet<>();
        private final List<Long> acquired = new ArrayList<>();
        private final List<Long> released = new ArrayList<>();

        @Override
        public boolean tryAcquire(Long ruleId) {
            if (exhausted.contains(ruleId)) {
                return false;
            }
            acquired.add(ruleId);
            return true;
        }

        @Override
        public void release(Long ruleId) {
            released.add(ruleId);
        }
    }
}
