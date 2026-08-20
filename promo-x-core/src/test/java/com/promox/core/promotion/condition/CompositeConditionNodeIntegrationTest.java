package com.promox.core.promotion.condition;


import com.promox.core.promotion.internal.dto.LineItemRequest;
import com.promox.core.promotion.internal.dto.PromotionEvaluationRequest;
import com.promox.core.promotion.internal.service.condition.CompositeConditionNode;
import com.promox.core.promotion.internal.service.condition.Operator;
import com.promox.core.promotion.internal.service.condition.ProductQuantityConditionNode;
import com.promox.core.promotion.internal.context.EngineWorkspace;

import static com.promox.core.promotion.internal.service.condition.CompositeConditionNode.LogicalOperator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;


public class CompositeConditionNodeIntegrationTest {
    record ItemSpec(String productId, int quantity) {}

    static EngineWorkspace buildTestWorkspace(List<ItemSpec> specs) {
        List<LineItemRequest> items = new ArrayList<>();
        for (ItemSpec spec : specs) {
            items.add(new LineItemRequest(
                    spec.productId(),
                    spec.productId() + "-SKU",
                    List.of("Electronics"),
                    List.of("Default"),
                    spec.quantity(),
                    new BigDecimal("50.00")
            ));
        }

        return new EngineWorkspace(new PromotionEvaluationRequest(
                123L, "sess-1", "cust-1", List.of(), items, BigDecimal.ZERO
        ));
    }

    @Test
    @DisplayName("Bundle Discount Rule: Requires 1x Phone AND 2x Cases")
    void bundleDiscount_RequiresBothItemsToMatch() {
        var phoneCondition = new ProductQuantityConditionNode(
                "prod-phone", 1, Operator.GREATER_THAN_OR_EQUAL
        );
        var caseCondition = new ProductQuantityConditionNode(
                "prod-case", 2, Operator.GREATER_THAN_OR_EQUAL
        );

        var bundleRule = new CompositeConditionNode(
                LogicalOperator.AND,
                List.of(phoneCondition, caseCondition)
        );

        // Cart 1: Phone only -> Fails
        EngineWorkspace phoneOnlyCart = buildTestWorkspace(List.of(
                new ItemSpec("prod-phone", 1)
        ));
        assertThat(bundleRule.evaluate(phoneOnlyCart)).isFalse();

        // Cart 2: Phone + 1 Case (needs 2) -> Fails
        EngineWorkspace insufficientCasesCart = buildTestWorkspace(List.of(
                new ItemSpec("prod-phone", 1),
                new ItemSpec("prod-case", 1)
        ));
        assertThat(bundleRule.evaluate(insufficientCasesCart)).isFalse();

        // Cart 3: Phone + 2 Cases -> Passes
        EngineWorkspace validBundleCart = buildTestWorkspace(List.of(
                new ItemSpec("prod-phone", 1),
                new ItemSpec("prod-case", 2)
        ));
        assertThat(bundleRule.evaluate(validBundleCart)).isTrue();
    }


    @Test
    @DisplayName("Alternative Item Rule: Requires 1x MacBook OR 1x iPad")
    void alternativeItemPromo_MatchesEitherProduct() {
        var macbookCondition = new ProductQuantityConditionNode("prod-macbook", 1, Operator.EQUALS);
        var ipadCondition = new ProductQuantityConditionNode("prod-ipad", 1, Operator.EQUALS);

        var hardwarePromoRule = new CompositeConditionNode(
                LogicalOperator.OR,
                List.of(macbookCondition, ipadCondition)
        );

        // Cart with iPad -> Passes
        EngineWorkspace ipadCart = buildTestWorkspace(List.of(
                new ItemSpec("prod-ipad", 1)
        ));
        assertThat(hardwarePromoRule.evaluate(ipadCart)).isTrue();

        // Cart with unrelated item -> Fails
        EngineWorkspace monitorCart = buildTestWorkspace(List.of(
                new ItemSpec("prod-monitor", 1)
        ));
        assertThat(hardwarePromoRule.evaluate(monitorCart)).isFalse();
    }


    @Test
    @DisplayName("Nested Rule: Charger AND (iPhone OR Galaxy)")
    void nestedCompositeRule_EvaluatesComplexHierarchy() {
        var chargerCondition = new ProductQuantityConditionNode("prod-charger", 1, Operator.GREATER_THAN_OR_EQUAL);
        var iphoneCondition = new ProductQuantityConditionNode("prod-iphone", 1, Operator.GREATER_THAN_OR_EQUAL);
        var galaxyCondition = new ProductQuantityConditionNode("prod-galaxy", 1, Operator.GREATER_THAN_OR_EQUAL);

        // Inner OR group
        var phoneChoiceGroup = new CompositeConditionNode(
                LogicalOperator.OR,
                List.of(iphoneCondition, galaxyCondition)
        );

        // Outer AND group combining a leaf node with a composite node
        var mainPromoRule = new CompositeConditionNode(
                LogicalOperator.AND,
                List.of(chargerCondition, phoneChoiceGroup)
        );

        // Case 1: Charger + Galaxy -> Passes
        EngineWorkspace validCart = buildTestWorkspace(List.of(
                new ItemSpec("prod-charger", 1),
                new ItemSpec("prod-galaxy", 1)
        ));
        assertThat(mainPromoRule.evaluate(validCart)).isTrue();

        // Case 2: iPhone only (missing mandatory charger) -> Fails
        EngineWorkspace missingChargerCart = buildTestWorkspace(List.of(
                new ItemSpec("prod-iphone", 1)
        ));
        assertThat(mainPromoRule.evaluate(missingChargerCart)).isFalse();
    }
}
