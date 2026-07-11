package com.promox.core.promotion.internal.action;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.promox.core.promotion.internal.context.EngineWorkspace;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = OrderDiscountAction.class, name = "ORDER_FLAT_DISCOUNT"),
        @JsonSubTypes.Type(value = OrderPercentageDiscountAction.class, name = "ORDER_PERCENTAGE_DISCOUNT")
})
public interface ActionStrategy {
    void apply(EngineWorkspace workspace);
}