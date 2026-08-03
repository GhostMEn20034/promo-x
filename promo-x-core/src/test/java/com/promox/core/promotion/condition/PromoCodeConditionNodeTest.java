package com.promox.core.promotion.condition;

import com.promox.core.promotion.dto.LineItemRequest;
import com.promox.core.promotion.dto.PromotionEvaluationRequest;
import com.promox.core.promotion.internal.condition.Operator;
import com.promox.core.promotion.internal.condition.PromoCodeConditionNode;
import com.promox.core.promotion.internal.context.EngineWorkspace;
import com.promox.core.promotion.internal.exception.InvalidConditionNodeException;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.util.stream.Stream;
import java.util.List;


public class PromoCodeConditionNodeTest {
    private static EngineWorkspace buildTestWorkspace(List<String> promoCodes) {
        LineItemRequest item = new LineItemRequest(
                "prod-100",
                "sku-100-black",
                List.of("Electronics"),
                List.of("Summer-Sale"),
                1,
                new BigDecimal("15.01")
        );

        PromotionEvaluationRequest request = new PromotionEvaluationRequest(
                123L,
                "sess-999",
                "cust-456",
                promoCodes,
                List.of(item),
                BigDecimal.ZERO
        );

        return new EngineWorkspace(request);
    }

    static Stream<Arguments> promoCodeProvider() {

        /*
        The Stream's content:
        1) Target promo code
        2) The operator used in the condition node
        3) Test Workspace
        4) Expected evaluation result in the condition node
        5) Expected exception when attempting to create the condition node (Nullable)
        */
        return Stream.of(
                // Success Cases (EQUALS)
                arguments("PROMO1", Operator.EQUALS, buildTestWorkspace(List.of("PROMO1")), true, null),
                arguments("PROMO1", Operator.EQUALS, buildTestWorkspace(List.of("PROMO1", "PROMO4", "promo2")), true, null),

                arguments("PROMO1", Operator.EQUALS,  buildTestWorkspace(List.of("promo1")), true, null), // Case-insensitive matching
                arguments("PROMO1", Operator.EQUALS, buildTestWorkspace(List.of("PROMO4")), false, null),

                // Success Cases (NOT_EQUALS)
                arguments("PROMO1", Operator.NOT_EQUALS, buildTestWorkspace(List.of("PROMO1")), false, null),
                arguments("PROMO1", Operator.NOT_EQUALS, buildTestWorkspace(List.of("PROMO4")), true, null),

                // Constructor Failure Cases (Throws Exception)
                arguments("PROMO1", Operator.GREATER_THAN_OR_EQUAL, buildTestWorkspace(List.of("PROMO1")), false, InvalidConditionNodeException.class),
                arguments(null, Operator.EQUALS, buildTestWorkspace(List.of("PROMO1")), false, InvalidConditionNodeException.class),
                arguments("   ", Operator.EQUALS, buildTestWorkspace(List.of("PROMO1")), false, InvalidConditionNodeException.class)
        );
    }

    @ParameterizedTest(name = "[{index}] Target Code: ''{0}'' | Operator: {1} | Cart Code: ''{2}'' -> Expected Result: {3}")
    @MethodSource("promoCodeProvider")
    void testPromoCodeConditionNode(
            String nodeCode,
            Operator operator,
            EngineWorkspace workspace,
            boolean expectedResult,
            Class<? extends Throwable> expectedException
    ) {
        if (expectedException != null) {
            assertThatThrownBy(() -> new PromoCodeConditionNode(nodeCode, operator))
                    .isInstanceOf(expectedException);
            return;
        }

        PromoCodeConditionNode node = new PromoCodeConditionNode(nodeCode, operator);

        assertThat(node.evaluate(workspace))
                .as("Evaluation of %s against workspace promo codes %s", node, workspace.getPromoCodes().getFirst())
                .isEqualTo(expectedResult);
    }
}
