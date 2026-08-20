package com.promox.core.promotion.condition;

import com.promox.core.promotion.internal.dto.LineItemRequest;
import com.promox.core.promotion.internal.dto.PromotionEvaluationRequest;
import com.promox.core.promotion.internal.service.condition.Operator;
import com.promox.core.promotion.internal.service.condition.ProductCategoryAnyMatchConditionNode;
import com.promox.core.promotion.internal.service.condition.SingleProductCategoryConditionNode;
import com.promox.core.promotion.internal.context.EngineWorkspace;
import com.promox.core.promotion.internal.exception.InvalidConditionNodeException;
import static com.promox.core.promotion.internal.exception.ConditionNodeErrorCodes.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.assertj.core.api.Assertions.*;


import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;


public class ProductCategoryMatchConditionNodeTest {
    static EngineWorkspace buildTestWorkspace(Map<String, List<String>> productIdCategoriesMap) {
        List<LineItemRequest> items = new ArrayList<>();

        productIdCategoriesMap.forEach((productId, categories) -> {
            var item = new LineItemRequest(
                    productId,
                    "%s-SKU".formatted(productId),
                    categories,
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

    // Product category any match condition node matrix
    static Stream<Arguments> categoryListEvaluationMatrixProvider() {
        List<String> targetCategories = List.of("Electronics", "Home");

        /*
        The Stream's structure:
        1) Target Categories
        2) The operator used in the condition node
        3) Test Workspace
        4) Expected evaluation result in the condition node
        */
        return Stream.of(
                // Empty Cart Use Case (Explicitly returns false for an empty cart per business rule)
                arguments(targetCategories, Operator.EQUALS, buildTestWorkspace(Map.of()), false),

                // Single Item Cart: Intersecting Categories Present (Matches 'Electronics')
                arguments(targetCategories, Operator.EQUALS, buildTestWorkspace(Map.of("p1", List.of("Electronics", "Books"))), true),

                // Single Item Cart: Intersecting Categories Present (Matches 'Home')
                arguments(targetCategories, Operator.EQUALS, buildTestWorkspace(Map.of("p2", List.of("Garden", "Home"))), true),

                // Single Item Cart: Completely Disjoint Categories Present
                arguments(targetCategories, Operator.EQUALS, buildTestWorkspace(Map.of("p3", List.of("Apparel", "Beauty"))), false),

                // Case-Insensitive Verification. Should match categories despite the character case
                arguments(targetCategories, Operator.EQUALS, buildTestWorkspace(Map.of("p1", List.of("ELECTRONICS", "HOME"))), true),

                // Multi-Item Cart: One line item matches one of the target categories from the allowlist
                arguments(targetCategories, Operator.EQUALS, buildTestWorkspace(Map.of(
                        "p1", List.of("Apparel"),
                        "p2", List.of("Home", "Garden"),
                        "p3", List.of("Beauty")
                )), true),

                // Multi-Item Cart: Multiple distinct line items match different targeted allowlist categories
                arguments(targetCategories, Operator.EQUALS, buildTestWorkspace(Map.of(
                        "p1", List.of("Electronics"),
                        "p2", List.of("Home"),
                        "p3", List.of("Books")
                )), true),

                // Multi-Item Cart: Completely disjoint categories across all line items in the collection
                arguments(targetCategories, Operator.EQUALS, buildTestWorkspace(Map.of(
                        "p1", List.of("Apparel"),
                        "p2", List.of("Automotive"),
                        "p3", List.of("Beauty")
                )), false)
        );
    }

    @ParameterizedTest(name = "[{index}] CategoryAnyMatch -> Operator: {1} | Expects: {3}")
    @MethodSource("categoryListEvaluationMatrixProvider")
    void evaluate_ShouldReturnExpectedBooleanValueWhenVerifyingIfAnyTargetCategoryIntersectsWithLineItemCategoryCollections(
            List<String> targetCategories,
            Operator operator,
            EngineWorkspace workspace,
            boolean expectedResult
    ) {
        var node = new ProductCategoryAnyMatchConditionNode(targetCategories, operator);

        assertThat(node.evaluate(workspace))
                .as("Product Category Any Match evaluation failed for Operator: %s", operator)
                .isEqualTo(expectedResult);
    }


    // Single product category condition node matrix
    static Stream<Arguments> singleCategoryEvaluationMatrixProvider() {
        String targetCategory = "Electronics";

        /*
        The Stream's structure:
        1) Target Category
        2) The operator used in the condition node
        3) Test Workspace
        4) Expected evaluation result in the condition node
        */
        return Stream.of(
                // Operator: Operator.EQUALS
                // Empty Cart Use Cases
                arguments(targetCategory, Operator.EQUALS, buildTestWorkspace(Map.of()), false),

                // Single Item Cart: Exact Category Matches Present
                arguments(targetCategory, Operator.EQUALS, buildTestWorkspace(Map.of("p1", List.of("Electronics", "Gadgets"))), true),

                // Single Item Cart: Entirely Different Categories Present
                arguments(targetCategory, Operator.EQUALS, buildTestWorkspace(Map.of("p2", List.of("Home", "Kitchen"))), false),

                // Case-Insensitive Verification. Must be case-insensitive
                arguments(targetCategory, Operator.EQUALS, buildTestWorkspace(Map.of("p1", List.of("ELECTRONICS"))), true),

                // Multi-Item Cart: Target category present in one of the multiple line items
                arguments(targetCategory, Operator.EQUALS, buildTestWorkspace(Map.of(
                        "p1", List.of("Home", "Kitchen"),
                        "p2", List.of("Electronics", "Gadgets"),
                        "p3", List.of("Apparel")
                )), true),

                // Multi-Item Cart: Target category completely absent across all multiple line items
                arguments(targetCategory, Operator.EQUALS, buildTestWorkspace(Map.of(
                        "p1", List.of("Home", "Kitchen"),
                        "p2", List.of("Books"),
                        "p3", List.of("Apparel")
                )), false),
                // Operator: Operator.NOT_EQUALS
                // Empty Cart Use Case
                arguments(targetCategory, Operator.NOT_EQUALS, buildTestWorkspace(Map.of()), true),

                // Single Item Cart: Category does not match target (Should pass NOT_EQUALS)
                arguments(targetCategory, Operator.NOT_EQUALS, buildTestWorkspace(Map.of("p1", List.of("Home", "Kitchen"))), true),

                // Single Item Cart: Category matches target exactly (Should fail NOT_EQUALS)
                arguments(targetCategory, Operator.NOT_EQUALS, buildTestWorkspace(Map.of("p1", List.of("Electronics", "Gadgets"))), false),

                // Multi-Item Cart: No category matches at all must return true
                arguments(targetCategory, Operator.NOT_EQUALS, buildTestWorkspace(Map.of(
                        "p1", List.of("Dishwashers"),
                        "p2", List.of("Home", "Kitchen")
                )), true),

                // Multi-Item Cart: All line items only contain the target category
                arguments(targetCategory, Operator.NOT_EQUALS, buildTestWorkspace(Map.of(
                        "p1", List.of("Electronics"),
                        "p2", List.of("Electronics", "Gadgets")
                )), false)
        );
    }

    @ParameterizedTest(name = "[{index}] SingleCategoryMatch -> Operator: {1} | Expects: {3}")
    @MethodSource("singleCategoryEvaluationMatrixProvider")
    void evaluate_ShouldReturnExpectedBooleanValueCaseSensitivelyWhenVerifyingSingleCategoryContainmentAgainstLineItems(
            String targetCategory,
            Operator operator,
            EngineWorkspace workspace,
            boolean expectedResult
    ) {
        var node = new SingleProductCategoryConditionNode(targetCategory, operator);

        assertThat(node.evaluate(workspace))
                .as("Single Product Category evaluation failed for Operator: %s", operator)
                .isEqualTo(expectedResult);
    }

    @ParameterizedTest
    @EnumSource(
            value = Operator.class,
            names = {"EQUALS"},
            mode = EnumSource.Mode.EXCLUDE
    )
    void constructor_ShouldThrowInvalidConditionNodeExceptionWithUnsupportedOperatorCode_WhenCategoryAnyMatchNodeGivenIllegalOperator(
            Operator operator
    ) {
        assertThatThrownBy(() -> new ProductCategoryAnyMatchConditionNode(List.of("Books"), operator))
                .isInstanceOf(InvalidConditionNodeException.class)
                .as("The AnyMatch constructor failed to reject the contextually illegal NOT_EQUALS operator")
                .satisfies(ex ->
                        assertThat(((InvalidConditionNodeException) ex).getErrorCode())
                                .isEqualTo(UNSUPPORTED_OPERATOR)
                );
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
        assertThatThrownBy(() -> new SingleProductCategoryConditionNode("Books", operator))
                .isInstanceOf(InvalidConditionNodeException.class)
                .as("The constructor failed to reject the contextually illegal NOT_EQUALS operator")
                .satisfies(
                        ex ->
                                assertThat(((InvalidConditionNodeException) ex).getErrorCode())
                                        .isEqualTo(UNSUPPORTED_OPERATOR)
                );
    }
}
