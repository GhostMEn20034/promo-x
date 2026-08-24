package com.promox.core.promotion.internal.infrastructure.registry;

import com.promox.core.promotion.internal.service.condition.*;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Component
public class ConditionNodeRegistry {
    private final Map<String, ConditionNodeSpec> specsByType = Stream.of(
        CompositeConditionNode.SPEC,
        CustomerAuthenticationStateConditionNode.SPEC,
        ProductAnyMatchConditionNode.SPEC,
        ProductCategoryAnyMatchConditionNode.SPEC,
        ProductCollectionAnyMatchConditionNode.SPEC,
        ProductQuantityConditionNode.SPEC,
        ProductSkuAnyMatchConditionNode.SPEC,
        ProductSkuQuantityConditionNode.SPEC,
        PromoCodeConditionNode.SPEC,
        SingleProductCategoryConditionNode.SPEC,
        SingleProductCollectionConditionNode.SPEC,
        SingleProductConditionNode.SPEC,
        SingleProductSkuConditionNode.SPEC,
        SubtotalConditionNode.SPEC
    ).collect(Collectors.toMap(ConditionNodeSpec::typeName, Function.identity()));

    public Collection<ConditionNodeSpec> getAllSpecs() {
        return Collections.unmodifiableCollection(specsByType.values());
    }

    public ConditionNodeSpec getSpec(String typeName) {
        ConditionNodeSpec spec = specsByType.get(typeName);
        if (spec == null) {
            throw new IllegalStateException("No spec registered for action type: " + typeName);
        }
        return spec;
    }
}
