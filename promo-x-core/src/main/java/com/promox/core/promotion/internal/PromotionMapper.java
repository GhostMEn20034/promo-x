package com.promox.core.promotion.internal;

import com.promox.core.product.DefaultProductDto;
import com.promox.core.promotion.internal.dto.*;
import com.promox.core.promotion.internal.infrastructure.registry.ActionStrategyRegistry;
import com.promox.core.promotion.internal.infrastructure.registry.ConditionNodeRegistry;
import com.promox.core.promotion.internal.persistence.PromotionRule;

import com.promox.core.promotion.internal.persistence.PromotionRuleSummaryProjection;
import org.springframework.stereotype.Component;

import java.util.List;


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
            rule.getProductExternalIds()
        );
    }

    public DetailedPromotionDto toDetailedDto(PromotionRule rule, List<DefaultProductDto> products) {
        return new DetailedPromotionDto(
            toBaseDto(rule),
            products
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