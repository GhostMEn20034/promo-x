package com.promox.core.promotion.internal.service.condition;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.promox.core.promotion.internal.context.EngineWorkspace;

import java.io.Serializable;
import java.util.Set;


// Serializable because rules are cached with Java serialization; implementations' fields must be serializable too.
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
public interface ConditionNode extends Serializable {
    @JsonIgnore
    String getTypeName();
    /**
     * Returns products' identifiers (Currently external IDs) mentioned in the condition node
     */
    @JsonIgnore
    default Set<String> getProductIdentifiers() {
        return Set.of();
    }

    /**
     * Returns categories' identifiers mentioned in the condition node
     */
    @JsonIgnore
    default Set<String> getCategoryIds() {
        return Set.of();
    }

    boolean evaluate(EngineWorkspace workspace);
}
