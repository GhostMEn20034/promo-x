package com.promox.core.promotion.condition;

import com.promox.core.promotion.dto.LineItemRequest;
import com.promox.core.promotion.dto.PromotionEvaluationRequest;
import com.promox.core.promotion.internal.condition.*;
import com.promox.core.promotion.internal.context.EngineWorkspace;
import com.promox.core.promotion.internal.exception.InvalidConditionNodeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.promox.core.promotion.internal.exception.ConditionNodeErrorCodes.UNSUPPORTED_OPERATOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;


public class ProductIdMatchConditionNodeTest {
    static EngineWorkspace buildTestWorkspace(Map<String, String> productIdToSkuMap) {
        List<LineItemRequest> items = new ArrayList<>();

        productIdToSkuMap.forEach((productId, sku) -> {
            var item = new LineItemRequest(
                    productId,
                    sku,
                    List.of("Electronics"),
                    List.of("Summer-Sale"),
                    1,
                    new BigDecimal("15.01")
            );
            items.add(item);
        });

        PromotionEvaluationRequest request = new PromotionEvaluationRequest(
                123L,
                "sess-999",
                "cust-456",
                List.of(),
                items,
                BigDecimal.ZERO
        );

        return new EngineWorkspace(request);
    }


    // product id any match condition node matrix
    static Stream<Arguments> productIdListEvaluationMatrixProvider() {
        List<String> targets = List.of("prod-apple-14", "prod-samsung-s24");

        /*
        The Stream's structure:
        1) Target Product IDs
        2) The operator used in the condition node
        3) Test Workspace
        4) Expected evaluation result in the condition node
        */
        return Stream.of(
                // Empty Cart Use Cases
                arguments(targets, Operator.EQUALS, buildTestWorkspace(Map.of()), false),
                arguments(targets, Operator.NOT_EQUALS, buildTestWorkspace(Map.of()), true),

                // Exact Matches Present
                arguments(targets, Operator.EQUALS, buildTestWorkspace(Map.of("prod-apple-14", "s1")), true),
                arguments(targets, Operator.NOT_EQUALS, buildTestWorkspace(Map.of("prod-apple-14", "s1")), false),

                // Case-Sensitive Verification (Should fail to match because product IDs are strict system keys)
                arguments(targets, Operator.EQUALS, buildTestWorkspace(Map.of("PROD-APPLE-14", "s1")), false),
                arguments(targets, Operator.NOT_EQUALS, buildTestWorkspace(Map.of("PROD-APPLE-14", "s1")), true)
        );
    }

    @ParameterizedTest(name = "[{index}] ProductIdListAnyMatch -> Operator: {1} | Expects: {3}")
    @MethodSource("productIdListEvaluationMatrixProvider")
    void evaluate_ShouldReturnExpectedBooleanValueWhenVerifyingProductIdCollectionContainmentAgainstLineItemsDynamicState(
            List<String> targetProductIds,
            Operator operator,
            EngineWorkspace workspace,
            boolean expectedResult
    ) {
        var node = new ProductIdAnyMatchConditionNode(targetProductIds, operator);

        assertThat(node.evaluate(workspace))
                .as("Product ID Collection matching evaluation failed for Operator: %s", operator)
                .isEqualTo(expectedResult);
    }


    // single product id condition node matrix
    static Stream<Arguments> singleProductIdEvaluationMatrixProvider() {
        String targetProductId = "prod-apple-14";

        /*
        The Stream's structure:
        1) Target Product ID
        2) The operator used in the condition node
        3) Test Workspace
        4) Expected evaluation result in the condition node
        */
        return Stream.of(
                // Empty Cart Use Cases
                arguments(targetProductId, Operator.EQUALS, buildTestWorkspace(Map.of()), false),
                arguments(targetProductId, Operator.NOT_EQUALS, buildTestWorkspace(Map.of()), true),

                // Exact Matches Present
                arguments(targetProductId, Operator.EQUALS, buildTestWorkspace(Map.of("prod-apple-14", "s1")), true),
                arguments(targetProductId, Operator.NOT_EQUALS, buildTestWorkspace(Map.of("prod-apple-14", "s1")), false),

                // Case-Sensitive Verification (Should fail to match because product IDs are strict system keys)
                arguments(targetProductId, Operator.EQUALS, buildTestWorkspace(Map.of("PROD-APPLE-14", "s1")), false),
                arguments(targetProductId, Operator.NOT_EQUALS, buildTestWorkspace(Map.of("PROD-APPLE-14", "s1")), true),
                // Exact Matches Absent
                arguments(targetProductId, Operator.NOT_EQUALS, buildTestWorkspace(Map.of("PROD-APPLE-16", "s1")), true),
                arguments(targetProductId, Operator.EQUALS, buildTestWorkspace(Map.of("PROD-APPLE-16", "s1")), false)
        );
    }

    @ParameterizedTest(name = "[{index}] SingleProductIdMatch -> Operator: {1} | Expects: {3}")
    @MethodSource("singleProductIdEvaluationMatrixProvider")
    void evaluate_ShouldReturnExpectedBooleanValueCaseSensitivelyWhenVerifyingSingleProductIdMatchAgainstLineItems(
            String targetProductId,
            Operator operator,
            EngineWorkspace workspace,
            boolean expectedResult
    ) {
        var node = new SingleProductIdConditionNode(targetProductId, operator);

        assertThat(node.evaluate(workspace))
                .as("Single Product ID evaluation failed for Operator: %s", operator)
                .isEqualTo(expectedResult);
    }

    @Test
    void constructor_ShouldThrowInvalidConditionNodeExceptionWithUnsupportedOperatorCode_WhenProductIdAnyMatchNodeGivenIllegalOperator() {
        assertThatThrownBy(() -> new ProductIdAnyMatchConditionNode(List.of("prod-1"), Operator.GREATER_THAN_OR_EQUAL))
                .isInstanceOf(InvalidConditionNodeException.class)
                .satisfies(ex -> assertThat(((InvalidConditionNodeException) ex).getErrorCode()).isEqualTo(UNSUPPORTED_OPERATOR));
    }
}
