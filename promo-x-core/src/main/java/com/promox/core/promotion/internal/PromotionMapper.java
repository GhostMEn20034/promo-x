package com.promox.core.promotion.internal;

import com.promox.core.promotion.internal.dto.ActionDto;
import com.promox.core.promotion.internal.dto.ConditionNodeDto;
import com.promox.core.promotion.internal.dto.DefaultPromotionDto;
import com.promox.core.promotion.internal.dto.PromotionBaseDto;
import com.promox.core.promotion.internal.infrastructure.registry.ActionStrategyRegistry;
import com.promox.core.promotion.internal.infrastructure.registry.ConditionNodeRegistry;
import com.promox.core.promotion.internal.persistence.PromotionRule;

import org.springframework.stereotype.Component;


@Component
public final class PromotionMapper {

    private final ActionStrategyRegistry actionRegistry;
    private final ConditionNodeRegistry conditionRegistry;

    public PromotionMapper(ActionStrategyRegistry actionRegistry, ConditionNodeRegistry conditionRegistry) {
        this.actionRegistry = actionRegistry;
        this.conditionRegistry = conditionRegistry;
    }

    public DefaultPromotionDto toDto(PromotionRule rule) {
        ConditionNodeDto conditionTree = new ConditionNodeDto(
            rule.getConditionTree(),
            conditionRegistry.getSpec(rule.getConditionTree().getTypeName())
        );
        ActionDto action = new ActionDto(
            rule.getAction(),
            actionRegistry.getSpec(rule.getAction().getTypeName())
        );

        return new DefaultPromotionDto(
            new PromotionBaseDto(
                rule.getId(),
                rule.getStorefrontId(),
                rule.getName(),
                rule.getPriority(),
                rule.isShortCircuit(),
                rule.isActive(),
                conditionTree,
                action,
                rule.getCreatedAt(),
                rule.getUpdatedAt()
            ),
            rule.getProductExternalIds()
        );
    }
}