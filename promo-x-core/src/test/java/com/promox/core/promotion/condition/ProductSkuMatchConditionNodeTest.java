package com.promox.core.promotion.condition;

import com.promox.core.promotion.dto.LineItemRequest;
import com.promox.core.promotion.dto.PromotionEvaluationRequest;
import com.promox.core.promotion.internal.condition.Operator;
import com.promox.core.promotion.internal.condition.ProductSkuAnyMatchConditionNode;
import com.promox.core.promotion.internal.condition.SingleProductSkuConditionNode;
import com.promox.core.promotion.internal.context.EngineWorkspace;
import static com.promox.core.promotion.internal.exception.ConditionNodeErrorCodes.*;


import com.promox.core.promotion.internal.exception.InvalidConditionNodeException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;


public class ProductSkuMatchConditionNodeTest {
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
                "us-storefront",
                "sess-999",
                "cust-456",
                List.of(),
                items,
                BigDecimal.ZERO
        );

        return new EngineWorkspace(request);
    }



    // product SKU any match condition node matrix
    static Stream<Arguments> skuListEvaluationMatrixProvider() {
        List<String> targets = List.of("sku-apple-14", "sku-samsung-s24");

        /*
        The Stream's structure:
        1) Target SKUs
        2) The operator used in the condition node
        3) Test Workspace
        4) Expected evaluation result in the condition node
        */
        return Stream.of(
                // Empty Cart Use Cases
                arguments(targets, Operator.EQUALS, buildTestWorkspace(Map.of()), false),
                arguments(targets, Operator.NOT_EQUALS, buildTestWorkspace(Map.of()), true),
                // Exact Matches Present
                arguments(targets, Operator.EQUALS, buildTestWorkspace(Map.of(
                        "p1", "sku-apple-14",
                        "p2", "sku-apple-17"
                        )
                ), true),
                arguments(targets, Operator.NOT_EQUALS, buildTestWorkspace(Map.of("p1", "sku-apple-14")), false),
                // Entirely Different SKUs Present
                arguments(targets, Operator.EQUALS, buildTestWorkspace(Map.of("p2", "sku-sony-ps5")), false),
                arguments(targets, Operator.NOT_EQUALS, buildTestWorkspace(Map.of("p2", "sku-sony-ps5")), true),
                // Case-Insensitive Match Verification (Using the nested stream fix)
                arguments(targets, Operator.EQUALS, buildTestWorkspace(Map.of("p1", "SKU-APPLE-14")), true),
                arguments(targets, Operator.NOT_EQUALS, buildTestWorkspace(Map.of("p1", "SKU-APPLE-14")), false)
        );
    }

    @ParameterizedTest(name = "[{index}] SkuListAnyMatch -> Operator: {1} | Expects: {3}")
    @MethodSource("skuListEvaluationMatrixProvider")
    void evaluate_ShouldReturnExpectedBooleanValueWhenVerifyingSkuCollectionContainmentAgainstLineItemsDynamicState(
            List<String> targetSkus,
            Operator operator,
            EngineWorkspace workspace,
            boolean expectedResult
    ) {
        var node = new ProductSkuAnyMatchConditionNode(targetSkus, operator);

        assertThat(node.evaluate(workspace))
                .as("SKU Collection matching evaluation failed for Operator: %s", operator)
                .isEqualTo(expectedResult);
    }


    // single product SKU condition node matrix
    static Stream<Arguments> singleSkuEvaluationMatrixProvider() {
        String targetSku = "sku-apple-14";

        /*
        The Stream's structure:
        1) Target SKU
        2) The operator used in the condition node
        3) Test Workspace
        4) Expected evaluation result in the condition node
        */
        return Stream.of(
                // Empty Cart Use Cases
                arguments(targetSku, Operator.EQUALS, buildTestWorkspace(Map.of()), false),
                arguments(targetSku, Operator.NOT_EQUALS, buildTestWorkspace(Map.of()), true),
                // Exact Matches Present
                arguments(targetSku, Operator.EQUALS, buildTestWorkspace(Map.of("p1", "sku-apple-14")), true),
                arguments(targetSku, Operator.NOT_EQUALS, buildTestWorkspace(Map.of("p1", "sku-apple-14")), false),
                // Case-Insensitive Matches Present
                arguments(targetSku, Operator.EQUALS, buildTestWorkspace(Map.of("p1", "SKU-APPLE-14")), true),
                arguments(targetSku, Operator.NOT_EQUALS, buildTestWorkspace(Map.of("p1", "SKU-APPLE-14")), false),
                // Exact Matches Absent
                arguments(targetSku, Operator.EQUALS, buildTestWorkspace(Map.of("p2", "sku-apple-15")), false),
                arguments(targetSku, Operator.NOT_EQUALS, buildTestWorkspace(Map.of("p1", "sku-apple-15")), true)
        );
    }

    @ParameterizedTest(name = "[{index}] SingleSkuMatch -> Operator: {1} | Expects: {3}")
    @MethodSource("singleSkuEvaluationMatrixProvider")
    void evaluate_ShouldReturnExpectedBooleanValueCaseInsensitivelyWhenVerifyingSingleSkuMatchAgainstLineItems(
            String targetSku,
            Operator operator,
            EngineWorkspace workspace,
            boolean expectedResult
    ) {
        var node = new SingleProductSkuConditionNode(targetSku, operator);

        assertThat(node.evaluate(workspace))
                .as("Single SKU case-insensitive matching evaluation failed for Operator: %s", operator)
                .isEqualTo(expectedResult);
    }

    @Test
    void constructor_ShouldThrowInvalidConditionNodeExceptionWithUnsupportedOperatorCode_WhenSkuAnyMatchNodeGivenIllegalOperator() {
        assertThatThrownBy(() -> new ProductSkuAnyMatchConditionNode(List.of("sku-1"), Operator.GREATER_THAN_OR_EQUAL))
                .isInstanceOf(InvalidConditionNodeException.class)
                .satisfies(
                    ex -> assertThat(((InvalidConditionNodeException) ex)
                            .getErrorCode())
                            .isEqualTo(UNSUPPORTED_OPERATOR)
                );
    }

}
