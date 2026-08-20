package com.promox.core.promotion.condition;

import com.promox.core.promotion.internal.dto.LineItemRequest;
import com.promox.core.promotion.internal.dto.PromotionEvaluationRequest;
import com.promox.core.promotion.internal.service.condition.Operator;
import com.promox.core.promotion.internal.service.condition.ProductSkuQuantityConditionNode;
import com.promox.core.promotion.internal.context.EngineWorkspace;
import com.promox.core.promotion.internal.exception.ConditionNodeErrorCodes;
import com.promox.core.promotion.internal.exception.InvalidConditionNodeException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;


public class ProductSkuQuantityConditionNodeTest {
    record SkuItemSpec(String sku, int quantity) {}

    static EngineWorkspace buildTestWorkspace(List<SkuItemSpec> itemsSpec) {
        List<LineItemRequest> items = new ArrayList<>();
        int index = 1;

        for (SkuItemSpec spec : itemsSpec) {
            var item = new LineItemRequest(
                    "prod-" + index,
                    spec.sku(),
                    List.of("Electronics"),
                    List.of("Default-Collection"),
                    spec.quantity(),
                    new BigDecimal("25.00")
            );
            items.add(item);
            index++;
        }

        var request = new PromotionEvaluationRequest(
                123L,
                "sess-999",
                "cust-456",
                List.of(),
                items,
                BigDecimal.ZERO
        );

        return new EngineWorkspace(request);
    }

    static Stream<Arguments> skuQuantityEvaluationMatrixProvider() {
        /*
        The Stream's structure:
        1) Target SKU
        2) Required Quantity
        3) The operator used in the condition node
        4) Test Workspace
        5) Expected evaluation result in the condition node
        */
        return Stream.of(
                // --- EMPTY CART USE CASES ---
                arguments("IPHONE-15-PRO", 1, Operator.EQUALS, buildTestWorkspace(List.of()), false),
                arguments("SAMSUNG-S24", 1, Operator.GREATER_THAN_OR_EQUAL, buildTestWorkspace(List.of()), false),

                // --- OPERATOR: EQUALS ---
                // Single Line Item: Exact match on required quantity
                arguments("IPHONE-15-PRO", 2, Operator.EQUALS, buildTestWorkspace(List.of(
                        new SkuItemSpec("IPHONE-15-PRO", 2)
                )), true),

                // Single Line Item: Quantity is less than required
                arguments("SAMSUNG-S24", 3, Operator.EQUALS, buildTestWorkspace(List.of(
                        new SkuItemSpec("SAMSUNG-S24", 2)
                )), false),

                // Single Line Item: Quantity is greater than required
                arguments("MACBOOK-M3-PRO", 2, Operator.EQUALS, buildTestWorkspace(List.of(
                        new SkuItemSpec("MACBOOK-M3-PRO", 4)
                )), false),

                // --- OPERATOR: GREATER_THAN_OR_EQUAL ---
                // Single Line Item: Quantity equals threshold
                arguments("SONY-WH1000XM5", 2, Operator.GREATER_THAN_OR_EQUAL, buildTestWorkspace(List.of(
                        new SkuItemSpec("SONY-WH1000XM5", 2)
                )), true),

                // Single Line Item: Quantity exceeds threshold
                arguments("NINTENDO-SWITCH-2", 2, Operator.GREATER_THAN_OR_EQUAL, buildTestWorkspace(List.of(
                        new SkuItemSpec("NINTENDO-SWITCH-2", 5)
                )), true),

                // Single Line Item: Quantity falls below threshold
                arguments("DELL-XPS-15", 3, Operator.GREATER_THAN_OR_EQUAL, buildTestWorkspace(List.of(
                        new SkuItemSpec("DELL-XPS-15", 2)
                )), false),

                // --- CASE INSENSITIVITY VERIFICATION ---
                arguments("pixel-8-pro", 2, Operator.EQUALS, buildTestWorkspace(List.of(
                        new SkuItemSpec("PIXEL-8-PRO", 2)
                )), true),

                arguments("AIRPODS-PRO-V2", 1, Operator.GREATER_THAN_OR_EQUAL, buildTestWorkspace(List.of(
                        new SkuItemSpec("airpods-pro-v2", 3)
                )), true),

                // --- MULTI-LINE ITEM QUANTITY AGGREGATION (.sum() check) ---
                // Split across multiple lines with the SAME target SKU
                arguments("LOGITECH-MX-MASTER", 5, Operator.EQUALS, buildTestWorkspace(List.of(
                        new SkuItemSpec("LOGITECH-MX-MASTER", 2),
                        new SkuItemSpec("LOGITECH-MX-MASTER", 3)
                )), true),

                arguments("KINDLE-PAPERWHITE", 4, Operator.GREATER_THAN_OR_EQUAL, buildTestWorkspace(List.of(
                        new SkuItemSpec("KINDLE-PAPERWHITE", 2),
                        new SkuItemSpec("KINDLE-PAPERWHITE", 3)
                )), true),

                // Mixed cart: Aggregates only matching target SKU, ignoring unrelated SKUs
                arguments("IPHONE-15-PRO", 3, Operator.EQUALS, buildTestWorkspace(List.of(
                        new SkuItemSpec("SAMSUNG-S24", 10),
                        new SkuItemSpec("IPHONE-15-PRO", 1),
                        new SkuItemSpec("IPHONE-15-PRO", 2),
                        new SkuItemSpec("MACBOOK-M3-PRO", 1)
                )), true),

                // Mixed cart: Non-matching SKUs present, target SKU quantity insufficient
                arguments("SAMSUNG-S24", 5, Operator.GREATER_THAN_OR_EQUAL, buildTestWorkspace(List.of(
                        new SkuItemSpec("IPHONE-15-PRO", 10),
                        new SkuItemSpec("SAMSUNG-S24", 2)
                )), false)
        );
    }

    @ParameterizedTest(name = "[{index}] Target SKU: {0} | Req Qty: {1} | Op: {2} | Expects: {4}")
    @MethodSource("skuQuantityEvaluationMatrixProvider")
    void evaluate_ShouldReturnExpectedBooleanValueWhenEvaluatingProductSkuQuantity(
            String targetSku,
            int requiredQuantity,
            Operator operator,
            EngineWorkspace workspace,
            boolean expectedResult
    ) {
        var node = new ProductSkuQuantityConditionNode(targetSku, requiredQuantity, operator);

        assertThat(node.evaluate(workspace))
                .as("Evaluation failed for SKU %s with operator %s", targetSku, operator)
                .isEqualTo(expectedResult);

    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void constructor_ShouldThrowInvalidConditionNodeException_WhenSkuIsBlank(String invalidSku) {
        assertThatThrownBy(() -> new ProductSkuQuantityConditionNode(invalidSku, 1, Operator.EQUALS))
                .isInstanceOf(InvalidConditionNodeException.class)
                .satisfies(ex -> assertThat(((InvalidConditionNodeException) ex).getErrorCode())
                        .isEqualTo(ConditionNodeErrorCodes.MISSING_REQUIRED_FIELD));
    }

    @Test
    void constructor_ShouldThrowInvalidConditionNodeException_WhenSkuIsNull() {
        assertThatThrownBy(() -> new ProductSkuQuantityConditionNode(null, 1, Operator.EQUALS))
                .isInstanceOf(InvalidConditionNodeException.class)
                .satisfies(ex -> assertThat(((InvalidConditionNodeException) ex).getErrorCode())
                        .isEqualTo(ConditionNodeErrorCodes.MISSING_REQUIRED_FIELD));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -100})
    void constructor_ShouldThrowInvalidConditionNodeException_WhenRequiredQuantityIsZeroOrNegative(int invalidQuantity) {
        assertThatThrownBy(() -> new ProductSkuQuantityConditionNode("SKU-1", invalidQuantity, Operator.EQUALS))
                .isInstanceOf(InvalidConditionNodeException.class)
                .satisfies(ex -> assertThat(((InvalidConditionNodeException) ex).getErrorCode())
                        .isEqualTo(ConditionNodeErrorCodes.INVALID_VALUE_RANGE));
    }

    @Test
    void constructor_ShouldThrowInvalidConditionNodeException_WhenOperatorIsUnsupported() {
        assertThatThrownBy(() -> new ProductSkuQuantityConditionNode("SKU-1", 1, Operator.NOT_EQUALS))
                .isInstanceOf(InvalidConditionNodeException.class)
                .satisfies(ex -> assertThat(((InvalidConditionNodeException) ex).getErrorCode())
                        .isEqualTo(ConditionNodeErrorCodes.UNSUPPORTED_OPERATOR));
    }
}
