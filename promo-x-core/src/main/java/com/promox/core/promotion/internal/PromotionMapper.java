package com.promox.core.promotion.internal;

import com.promox.core.product.DefaultProductDto;
import com.promox.core.promotion.internal.context.EngineWorkspace;
import com.promox.core.promotion.internal.dto.*;
import com.promox.core.promotion.internal.infrastructure.registry.ActionStrategyRegistry;
import com.promox.core.promotion.internal.infrastructure.registry.ConditionNodeRegistry;
import com.promox.core.promotion.internal.persistence.PromotionRule;

import com.promox.core.promotion.internal.persistence.PromotionRuleSummaryProjection;
import com.promox.core.promotion.internal.persistence.PromotionRuleUsage;
import jakarta.annotation.Nullable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;


@Component
public final class PromotionMapper {

    private final ActionStrategyRegistry actionRegistry;
    private final ConditionNodeRegistry conditionRegistry;

    public PromotionMapper(ActionStrategyRegistry actionRegistry, ConditionNodeRegistry conditionRegistry) {
        this.actionRegistry = actionRegistry;
        this.conditionRegistry = conditionRegistry;
    }

    private PromotionBaseDto toBaseDto(PromotionRule rule) {
        ConditionNodeDto conditionTree = new ConditionNodeDto(
            rule.getConditionTree(),
            conditionRegistry.getSpec(rule.getConditionTree().getTypeName())
        );
        ActionDto action = new ActionDto(
            rule.getAction(),
            actionRegistry.getSpec(rule.getAction().getTypeName())
        );

        return new PromotionBaseDto(
            rule.getId(),
            rule.getStorefrontId(),
            rule.getName(),
            rule.getPriority(),
            rule.isShortCircuit(),
            rule.isActive(),
            rule.hasUsageLimit(),
            rule.getUsageLimit(),
            rule.getRemainingUses(),
            conditionTree,
            action,
            rule.getCreatedAt(),
            rule.getUpdatedAt()
        );
    }

    public DefaultPromotionDto toDto(PromotionRule rule) {
        return new DefaultPromotionDto(
            toBaseDto(rule),
            // A copy, so the DTO never carries a lazy collection past the end of the transaction
            Set.copyOf(rule.getProductExternalIds())
        );
    }

    public DetailedPromotionDto toDetailedDto(PromotionRule rule, List<DefaultProductDto> products) {
        return new DetailedPromotionDto(
            toBaseDto(rule),
            products
        );
    }

    public PromotionRuleUsageDto toUsageDto(PromotionRuleUsage usage) {
        return new PromotionRuleUsageDto(
            usage.getId(),
            usage.getStatus(),
            usage.getExpiresAt()
        );
    }

    public PromotionEvaluationResponse toEvaluationResponse(
        EngineWorkspace workspace,
        @Nullable PromotionRuleUsage usage
    ) {
        List<PromotionEvaluationResponse.LineItemResult> items = workspace.getLineItems().stream()
            .map(item -> new PromotionEvaluationResponse.LineItemResult(
                item.getProductExternalId(),
                item.getProductSku(),
                item.getCategories(),
                item.getCollections(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getTotalPrice(),
                item.getDiscountAmount(),
                item.getNetPrice()
            ))
            .toList();

        List<PromotionEvaluationResponse.RuleResult> matchedRules = workspace.getMatchedRules().stream()
            .map(rule -> new PromotionEvaluationResponse.RuleResult(rule.ruleId(), rule.ruleName()))
            .toList();

        List<PromotionEvaluationResponse.RuleResult> appliedRules = workspace.getAppliedRules().stream()
            .map(rule -> new PromotionEvaluationResponse.RuleResult(rule.ruleId(), rule.ruleName()))
            .toList();

        BigDecimal oldTotal = workspace.getItemsSubtotal().add(workspace.getShippingCost());

        return new PromotionEvaluationResponse(
            workspace.getSessionId(),
            workspace.getCustomerId(),
            workspace.getPromoCodes(),
            oldTotal,
            workspace.getGrandTotal(),
            workspace.getTotalDiscount(),
            workspace.getItemsSubtotal(),
            workspace.getItemsDiscount(),
            workspace.getItemsNetSubtotal(),
            workspace.getShippingCost(),
            workspace.getShippingCostDiscount(),
            workspace.getNetShippingCost(),
            items,
            matchedRules,
            appliedRules,
            usage == null ? null : toUsageDto(usage)
        );
    }

    public PromotionSummaryDto toSummaryDto(PromotionRuleSummaryProjection projection) {
        return new PromotionSummaryDto(
            projection.getId(),
            projection.getStorefrontId(),
            projection.getName(),
            projection.getPriority(),
            projection.getIsShortCircuit(),
            projection.getIsActive(),
            projection.getCreatedAt(),
            projection.getUpdatedAt()
        );
    }
}