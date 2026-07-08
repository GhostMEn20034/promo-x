package com.promox.core.promotion.internal.condition;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.promox.core.promotion.internal.context.EngineWorkspace;


@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = CompositeConditionNode.class, name = "COMPOSITE"),
})
public interface ConditionNode {
    /**
     * Determines whether the given rule condition is satisfied by the current workspace state.
     */
    boolean evaluate(EngineWorkspace workspace);
}
