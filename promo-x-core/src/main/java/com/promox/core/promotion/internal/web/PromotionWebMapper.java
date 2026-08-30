package com.promox.core.promotion.internal.web;

import com.promox.core.promotion.internal.dto.ActionDto;
import com.promox.core.promotion.internal.dto.ConditionNodeDto;
import com.promox.core.promotion.internal.dto.DefaultPromotionDto;
import com.promox.core.promotion.internal.dto.PromotionBaseDto;
import com.promox.core.promotion.internal.dto.web.ConditionNodeWebResponseDto;
import com.promox.core.promotion.internal.dto.web.DefaultPromotionWebResponseDto;
import com.promox.core.promotion.internal.dto.web.PromotionBaseWebResponseDto;
import com.promox.core.promotion.internal.infrastructure.registry.ConditionNodeRegistry;
import com.promox.core.promotion.internal.service.condition.CompositeConditionNode;
import com.promox.core.promotion.internal.service.condition.ConditionNode;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;


@Component
public final class PromotionWebMapper {

    private final ConditionNodeRegistry conditionRegistry;
    private final JsonMapper jsonMapper;

    public PromotionWebMapper(ConditionNodeRegistry conditionRegistry, JsonMapper jsonMapper) {
        this.conditionRegistry = conditionRegistry;
        this.jsonMapper = jsonMapper;
    }

    public DefaultPromotionWebResponseDto toWebResponse(DefaultPromotionDto dto) {
        PromotionBaseDto base = dto.base();
        ConditionNodeDto conditionDto = base.conditionTree();
        ActionDto actionDto = base.action();

        PromotionBaseWebResponseDto baseWebDto = new PromotionBaseWebResponseDto(
            base.id(),
            base.storefrontId(),
            base.name(),
            base.priority(),
            base.isShortCircuit(),
            base.isActive(),
            new ConditionNodeWebResponseDto(toConditionNodeJson(conditionDto.node()), conditionDto.spec()),
            actionDto,
            base.createdAt(),
            base.updatedAt()
        );

        return new DefaultPromotionWebResponseDto(baseWebDto, dto.productExternalIds());
    }

    private JsonNode toConditionNodeJson(ConditionNode node) {
        ObjectNode json = jsonMapper.valueToTree(node);

        if (node instanceof CompositeConditionNode composite) {
            ArrayNode children = jsonMapper.createArrayNode();
            for (ConditionNode child : composite.children()) {
                ObjectNode childJson = (ObjectNode) toConditionNodeJson(child);
                childJson.set("spec", jsonMapper.valueToTree(conditionRegistry.getSpec(child.getTypeName())));
                children.add(childJson);
            }
            json.set("children", children);
        }

        return json;
    }
}