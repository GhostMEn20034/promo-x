package com.promox.core.promotion.condition;

import com.promox.core.promotion.dto.LineItemRequest;
import com.promox.core.promotion.dto.PromotionEvaluationRequest;
import com.promox.core.promotion.internal.condition.CustomerAuthenticationStateConditionNode;
import com.promox.core.promotion.internal.condition.Operator;
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
import java.util.List;
import java.util.stream.Stream;




public class CustomerAuthenticationConditionNodeTest {
    static EngineWorkspace buildTestWorkspace(boolean isCustomerAnonymous, boolean sessionIdIsNull) {
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
                sessionIdIsNull ? null : "sess-999",
                isCustomerAnonymous ? null : "cust-456",
                List.of(),
                List.of(item),
                BigDecimal.ZERO
        );

        return new EngineWorkspace(request);
    }

    static Stream<Arguments> customerAuthenticationEvaluationMatrixProvider() {
        /*
        The Stream's structure:
        1) Test Workspace
        2) The operator used in the condition node
        3) Expected evaluation result in the condition node
        */
        return Stream.of(
                // Authenticated customer use cases
                arguments(buildTestWorkspace(false, false), Operator.EQUALS, true),
                arguments(buildTestWorkspace(false, false), Operator.NOT_EQUALS, false),
                arguments(buildTestWorkspace(false, true), Operator.EQUALS, true),
                arguments(buildTestWorkspace(false, true), Operator.NOT_EQUALS, false),
                // Anonymous customer use cases
                arguments(buildTestWorkspace(true, false), Operator.EQUALS, false),
                arguments(buildTestWorkspace(true, true), Operator.EQUALS, false),
                arguments(buildTestWorkspace(true, false), Operator.NOT_EQUALS, true),
                arguments(buildTestWorkspace(true, true), Operator.NOT_EQUALS, true)
        );
    }

    @ParameterizedTest
    @MethodSource("customerAuthenticationEvaluationMatrixProvider")
    void evaluate_ShouldAccuratelyDetermineIfCustomerIsAuthenticatedOrAnonymousBasedOnTargetOperator_WhenSessionIdPresenceFluctuates(
            EngineWorkspace workspace,
            Operator operator,
            boolean expectedResult
    ) {
        var node = new CustomerAuthenticationStateConditionNode(operator);

        assertThat(node.evaluate(workspace))
                .as("Failed evaluating the authentication state using operator '%s' against the workspace state", operator)
                .isEqualTo(expectedResult);
    }

    @Test
    void constructor_ShouldThrowInvalidConditionNodeExceptionWithUnsupportedOperatorCode_WhenOperatorIsGreaterThanOrEqual() {
        // Arrange
        Operator unsupportedOperator = Operator.GREATER_THAN_OR_EQUAL;

        // Act & Assert
        assertThatThrownBy(() -> new CustomerAuthenticationStateConditionNode(unsupportedOperator))
                .isInstanceOf(InvalidConditionNodeException.class)
                .as("The constructor failed to reject the contextually illegal GREATER_THAN_OR_EQUAL operator")
                .satisfies(ex -> {
                    InvalidConditionNodeException exception = (InvalidConditionNodeException) ex;

                    assertThat(exception.getErrorCode())
                            .as("Exception did not carry the mandatory UNSUPPORTED_OPERATOR error code")
                            .isEqualTo(UNSUPPORTED_OPERATOR);
                });
    }
}
