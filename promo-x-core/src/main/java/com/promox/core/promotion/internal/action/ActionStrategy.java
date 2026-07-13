package com.promox.core.promotion.internal.action;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.promox.core.promotion.internal.context.EngineWorkspace;


@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
public interface ActionStrategy {
    String getTypeName();
    String getDisplayName();
    // Default fallback so you don't HAVE to write a description if you don't want to
    default String getDescription() {
        return "";
    }

    boolean apply(EngineWorkspace workspace);
}