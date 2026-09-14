package com.promox.core.promotion.condition;

import com.promox.core.promotion.internal.dto.LineItemRequest;
import com.promox.core.promotion.internal.dto.PromotionEvaluationRequest;
import com.promox.core.promotion.internal.service.condition.Operator;
import com.promox.core.promotion.internal.service.condition.SubtotalConditionNode;
import com.promox.core.promotion.internal.service.condition.SubtotalType;
import com.promox.core.promotion.internal.context.EngineWorkspace;

import com.promox.core.promotion.internal.exception.InvalidConditionNodeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static com.promox.core.promotion.internal.exception.ConditionNodeErrorCodes.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.assertj.core.api.Assertions.*;


import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;


public class SubtotalConditionNodeTest {
    private static final Long STOREFRONT_ID = 123L;
    /*
    * Helper method to construct a real EngineWorkspace for parameterized tests
    * */
    private EngineWorkspace buildTestEngineWorkspace(BigDecimal unitPrice, int totalQuantity) {
        List<LineItemRequest> items = getLineItems(unitPrice, totalQuantity);

        /*
         * We ignore Store Front ID, session ID,
         * customer ID, promo codes, shipping cost during the testing of subtotal nodes
         * */
        var request = new PromotionEvaluationRequest(
            "sess-999",
            "cust-456",
            List.of(),
            items,
            BigDecimal.ZERO
        );

        return new EngineWorkspace(STOREFRONT_ID, request);
    }

    private static List<LineItemRequest> getLineItems(BigDecimal unitPrice, int totalQuantity) {
        /*
         * We don't care about the product's ID, SKU, Category, Collection.
         * When we test subtotal condition node,
         * we only care how it's gonna behave with the different price tags and quantity of the line item
         * */
        List<LineItemRequest> items = new java.util.ArrayList<>();

        if (totalQuantity > 1) {
            // Dynamically split the total quantity across 2 completely distinct products
            int firstItemQty = totalQuantity / 2;
            int secondItemQty = totalQuantity - firstItemQty;

            items.add(new LineItemRequest(
                    "prod-100", "sku-100-black",
                    List.of("Electronics"), List.of("Sale"),
                    firstItemQty, unitPrice
            ));
            items.add(new LineItemRequest(
                    "prod-200", "sku-200-white",
                    List.of("Electronics"), List.of("Sale"),
                    secondItemQty, unitPrice
            ));
        } else {
            // Fallback for single-item edge cases
            items.add(new LineItemRequest(
                    "prod-100", "sku-100-black",
                    List.of("Electronics"), List.of("Sale"),
                    totalQuantity, unitPrice
            ));
        }
        return items;
    }

    /**
     * Define the core baseline values: (unitPrice, totalQuantity, threshold)
     */
    static Stream<Arguments> baseEvaluationMatrix() {
        return Stream.of(
                arguments(new BigDecimal("50.00"), 2, new BigDecimal("100.00")), // Total $100.00 (Exactly Equals Threshold)
                arguments(new BigDecimal("40.00"), 3, new BigDecimal("100.00")), // Total $120.00 (Greater Than Threshold)
                arguments(new BigDecimal("35.00"), 2, new BigDecimal("100.00"))  // Total $70.00  (Less Than Threshold)
        );
    }

    static Stream<Arguments> subtotalEvaluationProvider() {
        return baseEvaluationMatrix().flatMap(args -> {
            Object[] params = args.get();
            var unitPrice = (BigDecimal) params[0];
            var quantity = (int) params[1];
            var threshold = (BigDecimal) params[2];

            BigDecimal calculatedSubtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
            int comparison = calculatedSubtotal.compareTo(threshold);

            return Stream.of(
                    arguments(unitPrice, quantity, threshold, Operator.EQUALS,                SubtotalType.GROSS, comparison == 0),
                    arguments(unitPrice, quantity, threshold, Operator.GREATER_THAN_OR_EQUAL, SubtotalType.GROSS, comparison >= 0),
                    arguments(unitPrice, quantity, threshold, Operator.NOT_EQUALS,            SubtotalType.GROSS, comparison != 0),
                    arguments(unitPrice, quantity, threshold, Operator.EQUALS,                SubtotalType.NET,   comparison == 0),
                    arguments(unitPrice, quantity, threshold, Operator.GREATER_THAN_OR_EQUAL, SubtotalType.NET,   comparison >= 0),
                    arguments(unitPrice, quantity, threshold, Operator.NOT_EQUALS,            SubtotalType.NET,   comparison != 0)
            );
        });
    }

    static Stream<Arguments> messyConstructorInputProvider() {
        return Stream.of(
                // Use case 1: Threshold is completely missing
                arguments(null, Operator.EQUALS, SubtotalType.GROSS, MISSING_REQUIRED_FIELD),

                // Use case 2: Threshold dips into negative values
                arguments(new BigDecimal("-0.01"), Operator.EQUALS, SubtotalType.GROSS, INVALID_VALUE_RANGE),
                arguments(new BigDecimal("-150.75"), Operator.NOT_EQUALS, SubtotalType.NET, INVALID_VALUE_RANGE)
        );
    }

    @ParameterizedTest(name = "[{index}] Cart(${0}x{1} units) vs Threshold(${2}) | Op: {3} | Type: {4} -> Expects: {5}")
    @MethodSource("subtotalEvaluationProvider")
    void evaluate_ShouldAccuratelyDetermineConditionTruth(
            BigDecimal unitPrice,
            int quantity,
            BigDecimal threshold,
            Operator operator,
            SubtotalType type,
            boolean expectedResult
    ) {
        var node = new SubtotalConditionNode(threshold, operator, type);
        EngineWorkspace workspace = buildTestEngineWorkspace(unitPrice, quantity);


        assertThat(node.evaluate(workspace))
                .as("Failed evaluating a %s strategy logic on a %s condition where real subtotal " +
                        "is $%s and threshold is $%s")
                .isEqualTo(expectedResult);
    }

    @Test
    void evaluate_ShouldCalculateNetPriceDifferentlyThanGross_WhenPriorDiscountsAreApplied() {
        EngineWorkspace workspace = buildTestEngineWorkspace(new BigDecimal("15.01"), 1);

        var thresholdBeforeDiscount = new BigDecimal("15.01");
        var grossNode = new SubtotalConditionNode(thresholdBeforeDiscount, Operator.EQUALS, SubtotalType.GROSS);
        var netNode = new SubtotalConditionNode(thresholdBeforeDiscount, Operator.EQUALS, SubtotalType.NET);

        // Calculate condition nodes BEFORE the discount is applied
        assertThat(grossNode.evaluate(workspace))
                .as("Gross evaluation should evaluate against the original $15.01")
                .isTrue();

        assertThat(netNode.evaluate(workspace))
                .as("Net evaluation MUST evaluate against the original $15.01," +
                        "since no discount being applied")
                .isTrue();

        // Applying the discount
        workspace.getLineItems().getFirst().addDiscount(new BigDecimal("5.00"));

        // Calculate condition nodes AFTER the discount is applied
        assertThat(grossNode.evaluate(workspace))
                .as("Gross evaluation should evaluate against the original $15.01, " +
                        "despite the discount being applied")
                .isTrue();

        assertThat(netNode.evaluate(workspace))
                .as("Net evaluation MUST factor in side effects " +
                        "and evaluate against mutated $15.01 - $5.00 = $10.01")
                .isFalse();
    }

    @ParameterizedTest(name = "[{index}] Expecting Failure -> Threshold: {0} | Operator: {1} | Type: {2}")
    @MethodSource("messyConstructorInputProvider")
    void constructor_ShouldThrowInvalidConditionNodeExceptionAndRefuseToCreateNode_WhenArgumentsAreGarbageOrUnsupported(
            BigDecimal threshold,
            Operator operator,
            SubtotalType subtotalType,
            String expectedErrorCode
    ) {
        assertThatThrownBy(() ->
                new SubtotalConditionNode(threshold, operator, subtotalType))
                .isInstanceOf(InvalidConditionNodeException.class)
                // Unpacks the exception to verify the internal structural code field
                .satisfies(ex -> {
                    InvalidConditionNodeException exception = (InvalidConditionNodeException) ex;
                    assertThat(exception.getErrorCode())
                            .as("Constructor threw the right exception, but the error code was wrong")
                            .isEqualTo(expectedErrorCode);
                });
    }
}
