package com.promox.core.promotion.internal.infrastructure.registry;

import com.promox.core.promotion.internal.service.action.*;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class ActionStrategyRegistry {
    private final Map<String, ActionStrategySpec> specsByType = Stream.of(
        FreeShippingAction.SPEC,
        OrderDiscountAction.SPEC,
        OrderPercentageDiscountAction.SPEC,
        ProductBuyXGetYFreeAction.SPEC,
        ProductPercentageDiscountAction.SPEC,
        ProductSKUBuyXGetYFreeAction.SPEC,
        ProductSKUPercentageDiscountAction.SPEC,
        ShippingPercentageDiscountAction.SPEC
    ).collect(Collectors.toMap(ActionStrategySpec::typeName, Function.identity()));

    public Collection<ActionStrategySpec> getAllSpecs() {
        return Collections.unmodifiableCollection(specsByType.values());
    }

    public ActionStrategySpec getSpec(String typeName) {
        ActionStrategySpec spec = specsByType.get(typeName);
        if (spec == null) {
            throw new IllegalStateException("No spec registered for action type: " + typeName);
        }
        return spec;
    }
}
