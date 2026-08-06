package com.promox.core.promotion.condition;

import com.promox.core.promotion.dto.LineItemRequest;
import com.promox.core.promotion.dto.PromotionEvaluationRequest;
import com.promox.core.promotion.internal.condition.Operator;
import com.promox.core.promotion.internal.condition.ProductIdQuantityConditionNode;
import com.promox.core.promotion.internal.context.EngineWorkspace;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;


import com.promox.core.promotion.internal.exception.ConditionNodeErrorCodes;
import com.promox.core.promotion.internal.exception.InvalidConditionNodeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public class ProductIdQuantityConditionNodeTest {
    record ProductItemSpec(String productId, String sku, int quantity) {
        ProductItemSpec(String productId, int quantity) {
            this(productId, "%s-SKU".formatted(productId), quantity);
        }
    }

    static EngineWorkspace buildTestWorkspace(List<ProductItemSpec> itemsSpec) {
        List<LineItemRequest> items = new ArrayList<>();

        for (ProductItemSpec spec : itemsSpec) {
            var item = new LineItemRequest(
                    spec.productId(),
                    spec.sku(),
                    List.of("Electronics"),
                    List.of("Default-Collection"),
                    spec.quantity(),
                    new BigDecimal("25.00")
            );
            items.add(item);
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

    static Stream<Arguments> productIdQuantityEvaluationMatrixProvider() {
        /*
        The Stream's structure:
        1) Target Product ID
        2) Required Quantity
        3) The operator used in the condition node
        4) Test Workspace
        5) Expected evaluation result in the condition node
        */
        return Stream.of(
                // --- EMPTY CART USE CASES ---
                arguments("550e8400-e29b-41d4-a716-446655440000", 1, Operator.EQUALS, buildTestWorkspace(List.of()), false),
                arguments("prod-202", 1, Operator.GREATER_THAN_OR_EQUAL, buildTestWorkspace(List.of()), false),

                // --- OPERATOR: EQUALS ---
                // Single Line Item: Exact match on required quantity (UUID format)
                arguments("550e8400-e29b-41d4-a716-446655440000", 2, Operator.EQUALS, buildTestWorkspace(List.of(
                        new ProductItemSpec("550e8400-e29b-41d4-a716-446655440000", 2)
                )), true),

                // Single Line Item: Quantity is less than required
                arguments("prod-202", 3, Operator.EQUALS, buildTestWorkspace(List.of(
                        new ProductItemSpec("prod-202", 2)
                )), false),

                // Single Line Item: Quantity is greater than required
                arguments("prod-303", 2, Operator.EQUALS, buildTestWorkspace(List.of(
                        new ProductItemSpec("prod-303", 4)
                )), false),

                // --- OPERATOR: GREATER_THAN_OR_EQUAL ---
                // Single Line Item: Quantity equals threshold
                arguments("prod-404", 2, Operator.GREATER_THAN_OR_EQUAL, buildTestWorkspace(List.of(
                        new ProductItemSpec("prod-404", 2)
                )), true),

                // Single Line Item: Quantity exceeds threshold
                arguments("prod-505", 2, Operator.GREATER_THAN_OR_EQUAL, buildTestWorkspace(List.of(
                        new ProductItemSpec("prod-505", 5)
                )), true),

                // Single Line Item: Quantity falls below threshold
                arguments("prod-606", 3, Operator.GREATER_THAN_OR_EQUAL, buildTestWorkspace(List.of(
                        new ProductItemSpec("prod-606", 2)
                )), false),

                // --- STRICT CASE SENSITIVITY VERIFICATION ---
                // Lowercase target ID vs Uppercase cart item ID -> Must fail (returns false)
                arguments("550e8400-e29b-41d4-a716-446655440000", 2, Operator.EQUALS, buildTestWorkspace(List.of(
                        new ProductItemSpec("550E8400-E29B-41D4-A716-446655440000", 2)
                )), false),

                // Uppercase target ID vs Lowercase cart item ID -> Must fail (returns false)
                arguments("PROD-MACBOOK", 1, Operator.GREATER_THAN_OR_EQUAL, buildTestWorkspace(List.of(
                        new ProductItemSpec("prod-macbook", 3)
                )), false),

                // Mixed-case prefix mismatch -> Must fail
                arguments("prod-iphone15", 2, Operator.EQUALS, buildTestWorkspace(List.of(
                        new ProductItemSpec("PROD-IPHONE15", 2)
                )), false),

                // --- MULTI-LINE ITEM QUANTITY AGGREGATION (.sum() check across variants) ---
                // Aggregates quantities across different SKUs sharing the EXACT same parent Product ID
                arguments("prod-tshirt-uuid-1", 5, Operator.EQUALS, buildTestWorkspace(List.of(
                        new ProductItemSpec("prod-tshirt-uuid-1", "tshirt-red-medium", 2),
                        new ProductItemSpec("prod-tshirt-uuid-1", "tshirt-blue-large", 3)
                )), true),

                // Mixed cart: Aggregates matching Product ID while ignoring unrelated Product IDs
                arguments("prod-101", 3, Operator.EQUALS, buildTestWorkspace(List.of(
                        new ProductItemSpec("prod-999", "unrelated-sku-1", 10),
                        new ProductItemSpec("prod-101", "sku-red", 1),
                        new ProductItemSpec("prod-101", "sku-blue", 2),
                        new ProductItemSpec("prod-888", "unrelated-sku-2", 1)
                )), true),

                // Mixed cart: Casing mismatch on one line item prevents quantity aggregation
                arguments("prod-101", 3, Operator.EQUALS, buildTestWorkspace(List.of(
                        new ProductItemSpec("prod-101", "sku-red", 1),
                        new ProductItemSpec("PROD-101", "sku-blue", 2) // Uppercase ignored, total count = 1
                )), false)
        );
    }

    @ParameterizedTest(name = "[{index}] Target Product ID: {0} | Req Qty: {1} | Op: {2} | Expects: {4}")
    @MethodSource("productIdQuantityEvaluationMatrixProvider")
    void evaluate_ShouldReturnExpectedBooleanValueWhenEvaluatingProductIdQuantityStrictlyCaseSensitively(
            String targetProductId,
            int requiredQuantity,
            Operator operator,
            EngineWorkspace workspace,
            boolean expectedResult
    ) {
        var node = new ProductIdQuantityConditionNode(targetProductId, requiredQuantity, operator);

        assertThat(node.evaluate(workspace))
                .as("Evaluation failed for Product ID %s with operator %s", targetProductId, operator)
                .isEqualTo(expectedResult);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void constructor_ShouldThrowInvalidConditionNodeException_WhenProductIdIsBlank(String invalidProductId) {
        assertThatThrownBy(() -> new ProductIdQuantityConditionNode(invalidProductId, 1, Operator.EQUALS))
                .isInstanceOf(InvalidConditionNodeException.class)
                .satisfies(ex -> assertThat(((InvalidConditionNodeException) ex).getErrorCode())
                        .isEqualTo(ConditionNodeErrorCodes.MISSING_REQUIRED_FIELD));
    }

    @Test
    void constructor_ShouldThrowInvalidConditionNodeException_WhenProductIdIsNull() {
        assertThatThrownBy(() -> new ProductIdQuantityConditionNode(null, 1, Operator.EQUALS))
                .isInstanceOf(InvalidConditionNodeException.class)
                .satisfies(ex -> assertThat(((InvalidConditionNodeException) ex).getErrorCode())
                        .isEqualTo(ConditionNodeErrorCodes.MISSING_REQUIRED_FIELD));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -100})
    void constructor_ShouldThrowInvalidConditionNodeException_WhenRequiredQuantityIsZeroOrNegative(int invalidQuantity) {
        assertThatThrownBy(() -> new ProductIdQuantityConditionNode("prod-1", invalidQuantity, Operator.EQUALS))
                .isInstanceOf(InvalidConditionNodeException.class)
                .satisfies(ex -> assertThat(((InvalidConditionNodeException) ex).getErrorCode())
                        .isEqualTo(ConditionNodeErrorCodes.INVALID_VALUE_RANGE));
    }

    @Test
    void constructor_ShouldThrowInvalidConditionNodeException_WhenOperatorIsUnsupported() {
        assertThatThrownBy(() -> new ProductIdQuantityConditionNode("prod-1", 1, Operator.NOT_EQUALS))
                .isInstanceOf(InvalidConditionNodeException.class)
                .satisfies(ex -> assertThat(((InvalidConditionNodeException) ex).getErrorCode())
                        .isEqualTo(ConditionNodeErrorCodes.UNSUPPORTED_OPERATOR));
    }
}
