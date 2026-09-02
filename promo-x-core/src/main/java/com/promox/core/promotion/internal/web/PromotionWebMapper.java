package com.promox.core.promotion.internal.web;

import com.promox.core.promotion.internal.dto.*;
import com.promox.core.promotion.internal.dto.web.ConditionNodeWebResponseDto;
import com.promox.core.promotion.internal.dto.web.DefaultPromotionWebResponseDto;
import com.promox.core.promotion.internal.dto.web.DetailedPromotionWebResponseDto;
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

    private PromotionBaseWebResponseDto toBaseWebDto(PromotionBaseDto dto) {
        ConditionNodeDto conditionDto = dto.conditionTree();
        ActionDto actionDto = dto.action();

        return new PromotionBaseWebResponseDto(
            dto.id(),
            dto.storefrontId(),
            dto.name(),
            dto.priority(),
            dto.isShortCircuit(),
            dto.isActive(),
            new ConditionNodeWebResponseDto(toConditionNodeJson(conditionDto.node()), conditionDto.spec()),
            actionDto,
            dto.createdAt(),
            dto.updatedAt()
        );
    }

    public DefaultPromotionWebResponseDto toWebResponse(DefaultPromotionDto dto) {
        return new DefaultPromotionWebResponseDto(toBaseWebDto(dto.base()), dto.productExternalIds());
    }

    public DetailedPromotionWebResponseDto toDetailedWebResponse(DetailedPromotionDto dto) {
        return new DetailedPromotionWebResponseDto(
            toBaseWebDto(dto.base()),
            dto.products()
        );
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