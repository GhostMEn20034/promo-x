package com.promox.core.promotion.condition;

import com.promox.core.promotion.internal.dto.LineItemRequest;
import com.promox.core.promotion.internal.dto.PromotionEvaluationRequest;
import com.promox.core.promotion.internal.service.condition.Operator;
import com.promox.core.promotion.internal.service.condition.ProductCollectionAnyMatchConditionNode;
import com.promox.core.promotion.internal.service.condition.SingleProductCollectionConditionNode;
import com.promox.core.promotion.internal.context.EngineWorkspace;
import com.promox.core.promotion.internal.exception.InvalidConditionNodeException;
import static com.promox.core.promotion.internal.exception.ConditionNodeErrorCodes.UNSUPPORTED_OPERATOR;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;



public class ProductCollectionMatchConditionNodeTest {
    private static final Long STOREFRONT_ID = 123L;

    static EngineWorkspace buildTestWorkspace(Map<String, List<String>> productIdCollectionsMap) {
        List<LineItemRequest> items = new ArrayList<>();

        productIdCollectionsMap.forEach((productId, collections) -> {
            var item = new LineItemRequest(
                    productId,
                    "%s-SKU".formatted(productId),
                    List.of(),
                    collections,
                    1,
                    new BigDecimal("15.01")
            );
            items.add(item);
        });

        PromotionEvaluationRequest request = new PromotionEvaluationRequest(
                "sess-999",
                "cust-456",
                List.of(),
                items,
                BigDecimal.ZERO
        );

        return new EngineWorkspace(STOREFRONT_ID, request);
    }

    // single product collection condition node matrix
    static Stream<Arguments> singleCollectionEvaluationMatrixProvider() {
        String targetCollection = "Winter-Wear";

        return Stream.of(
                // --- OPERATOR: EQUALS ---
                // Empty Cart Use Case
                arguments(targetCollection, Operator.EQUALS, buildTestWorkspace(Map.of()), false),

                // Single Item Cart: Exact Collection Match
                arguments(targetCollection, Operator.EQUALS, buildTestWorkspace(Map.of("p1", List.of("Winter-Wear", "Apparel"))), true),

                // Single Item Cart: Entirely Different Collections
                arguments(targetCollection, Operator.EQUALS, buildTestWorkspace(Map.of("p2", List.of("Summer-Sale", "Accessories"))), false),

                // Case-Insensitive Verification
                arguments(targetCollection, Operator.EQUALS, buildTestWorkspace(Map.of("p1", List.of("WINTER-WEAR"))), true),

                // Multi-Item Cart: Target collection present in one of the multiple line items
                arguments(targetCollection, Operator.EQUALS, buildTestWorkspace(Map.of(
                        "p1", List.of("Summer-Sale"),
                        "p2", List.of("Winter-Wear", "Footwear"),
                        "p3", List.of("Clearance")
                )), true),

                // Multi-Item Cart: Target collection completely absent across all multiple line items
                arguments(targetCollection, Operator.EQUALS, buildTestWorkspace(Map.of(
                        "p1", List.of("Summer-Sale"),
                        "p2", List.of("Casual"),
                        "p3", List.of("Clearance")
                )), false),

                // --- OPERATOR: NOT_EQUALS ---
                // Empty Cart Use Case (Empty carts contain zero excluded collections)
                arguments(targetCollection, Operator.NOT_EQUALS, buildTestWorkspace(Map.of()), true),

                // Single Item Cart: Collection does not match target
                arguments(targetCollection, Operator.NOT_EQUALS, buildTestWorkspace(Map.of("p1", List.of("Summer-Sale"))), true),

                // Single Item Cart: Collection matches target exactly
                arguments(targetCollection, Operator.NOT_EQUALS, buildTestWorkspace(Map.of("p1", List.of("Winter-Wear"))), false),

                // Case-Insensitive Verification
                arguments(targetCollection, Operator.NOT_EQUALS, buildTestWorkspace(Map.of("p1", List.of("winter-wear"))), false),

                // Multi-Item Cart: At least one line item has a collection that matches the target (Fails NOT_EQUALS)
                arguments(targetCollection, Operator.NOT_EQUALS, buildTestWorkspace(Map.of(
                        "p1", List.of("Summer-Sale"),
                        "p2", List.of("WINTER-WEAR")
                )), false),

                // Multi-Item Cart: All line items completely clear of the targeted collection
                arguments(targetCollection, Operator.NOT_EQUALS, buildTestWorkspace(Map.of(
                        "p1", List.of("Summer-Sale"),
                        "p2", List.of("Casual", "Accessories")
                )), true)
        );
    }

    @ParameterizedTest(name = "[{index}] SingleCollectionMatch -> Operator: {1} | Expects: {3}")
    @MethodSource("singleCollectionEvaluationMatrixProvider")
    void evaluate_ShouldReturnExpectedBooleanValueCaseInsensitivelyWhenVerifyingSingleCollectionContainment(
            String targetCollection,
            Operator operator,
            EngineWorkspace workspace,
            boolean expectedResult
    ) {
        var node = new SingleProductCollectionConditionNode(targetCollection, operator);

        assertThat(node.evaluate(workspace))
                .as("Single Product Collection evaluation failed for Operator: %s", operator)
                .isEqualTo(expectedResult);
    }


    // product collection any match condition node matrix
    static Stream<Arguments> collectionListEvaluationMatrixProvider() {
        List<String> targetCollections = List.of("Winter-Wear", "Footwear");

        return Stream.of(
                // Empty Cart Use Case
                arguments(targetCollections, buildTestWorkspace(Map.of()), false),

                // Single Item Cart: Intersecting Collection Present (Matches 'Winter-Wear')
                arguments(targetCollections, buildTestWorkspace(Map.of("p1", List.of("Winter-Wear", "Apparel"))), true),

                // Single Item Cart: Intersecting Collection Present (Matches 'Footwear')
                arguments(targetCollections, buildTestWorkspace(Map.of("p2", List.of("Boots", "Footwear"))), true),

                // Single Item Cart: Completely Disjoint Collections Present
                arguments(targetCollections, buildTestWorkspace(Map.of("p3", List.of("Summer-Sale", "Accessories"))), false),

                // Case-Insensitive Verification
                arguments(targetCollections, buildTestWorkspace(Map.of("p1", List.of("WINTER-WEAR", "FOOTWEAR"))), true),

                // Multi-Item Cart: One line item matches one of the target collections from the allowlist
                arguments(targetCollections, buildTestWorkspace(Map.of(
                        "p1", List.of("Summer-Sale"),
                        "p2", List.of("Footwear", "Clearance"),
                        "p3", List.of("Accessories")
                )), true),

                // Multi-Item Cart: Multiple distinct line items match different targeted allowlist collections
                arguments(targetCollections, buildTestWorkspace(Map.of(
                        "p1", List.of("Winter-Wear"),
                        "p2", List.of("Footwear"),
                        "p3", List.of("Casual")
                )), true),

                // Multi-Item Cart: Completely disjoint collections across all line items
                arguments(targetCollections, buildTestWorkspace(Map.of(
                        "p1", List.of("Summer-Sale"),
                        "p2", List.of("Casual"),
                        "p3", List.of("Accessories")
                )), false)
        );
    }

    @ParameterizedTest(name = "[{index}] CollectionAnyMatch -> Expects: {2}")
    @MethodSource("collectionListEvaluationMatrixProvider")
    void evaluate_ShouldReturnExpectedBooleanValueCaseInsensitivelyWhenVerifyingIfAnyTargetCollectionIntersects(
            List<String> targetCollections,
            EngineWorkspace workspace,
            boolean expectedResult
    ) {
        var node = new ProductCollectionAnyMatchConditionNode(targetCollections);

        assertThat(node.evaluate(workspace))
                .as("Product Collection Any Match evaluation failed")
                .isEqualTo(expectedResult);
    }


    @ParameterizedTest
    @EnumSource(
            value = Operator.class,
            names = {"EQUALS", "NOT_EQUALS"},
            mode = EnumSource.Mode.EXCLUDE
    )
    void constructor_ShouldThrowInvalidConditionNodeExceptionWithUnsupportedOperatorCode_WhenSingleCategoryNodeGivenIllegalOperator(
            Operator operator
    ) {
        assertThatThrownBy(() -> new SingleProductCollectionConditionNode("Books", operator))
                .isInstanceOf(InvalidConditionNodeException.class)
                .as("The constructor failed to reject the contextually illegal NOT_EQUALS operator")
                .satisfies(
                        ex ->
                                assertThat(((InvalidConditionNodeException) ex).getErrorCode())
                                        .isEqualTo(UNSUPPORTED_OPERATOR)
                );
    }
}