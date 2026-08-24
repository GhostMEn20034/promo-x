package com.promox.core.promotion.internal;

import com.promox.core.promotion.internal.dto.ActionDto;
import com.promox.core.promotion.internal.dto.ConditionNodeDto;
import com.promox.core.promotion.internal.dto.DefaultPromotionDto;
import com.promox.core.promotion.internal.dto.PromotionBaseDto;
import com.promox.core.promotion.internal.infrastructure.registry.ActionStrategyRegistry;
import com.promox.core.promotion.internal.infrastructure.registry.ConditionNodeRegistry;
import com.promox.core.promotion.internal.persistence.PromotionRule;
import com.promox.core.promotion.internal.service.action.ActionStrategy;
import com.promox.core.promotion.internal.service.action.ActionStrategySpec;
import com.promox.core.promotion.internal.service.condition.CompositeConditionNode;
import com.promox.core.promotion.internal.service.condition.ConditionNode;
import com.promox.core.promotion.internal.service.condition.ConditionNodeSpec;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

@Component
public final class PromotionMapper {

    private final ActionStrategyRegistry actionRegistry;
    private final ConditionNodeRegistry conditionRegistry;
    private final JsonMapper jsonMapper;

    public PromotionMapper(ActionStrategyRegistry actionRegistry, ConditionNodeRegistry conditionRegistry, JsonMapper jsonMapper) {
        this.actionRegistry = actionRegistry;
        this.conditionRegistry = conditionRegistry;
        this.jsonMapper = jsonMapper;
    }

    public DefaultPromotionDto toDto(PromotionRule rule) {
        ConditionNodeDto conditionTree = toConditionNodeDto(rule.getConditionTree());
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

    private ConditionNodeDto toConditionNodeDto(ConditionNode node) {
        return new ConditionNodeDto(
            toConditionNodeJson(node),
            conditionRegistry.getSpec(node.getTypeName())
        );
    }

    private JsonNode toConditionNodeJson(ConditionNode node) {
        ObjectNode json = (ObjectNode) jsonMapper.valueToTree(node);

        if (node instanceof CompositeConditionNode composite) {
            ArrayNode children = jsonMapper.createArrayNode();
            for (ConditionNode child : composite.children()) {
                ObjectNode childJson = (ObjectNode) toConditionNodeJson(child); // recurses arbitrarily deep
                childJson.set("spec", jsonMapper.valueToTree(conditionRegistry.getSpec(child.getTypeName())));
                children.add(childJson);
            }
            json.set("children", children);
        }

        return json;
    }
}