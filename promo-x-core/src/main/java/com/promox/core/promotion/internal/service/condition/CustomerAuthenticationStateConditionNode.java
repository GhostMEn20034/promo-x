package com.promox.core.promotion.internal.service.condition;

import com.promox.core.promotion.internal.context.EngineWorkspace;
import com.promox.core.promotion.internal.exception.InvalidConditionNodeException;
import static com.promox.core.promotion.internal.exception.ConditionNodeErrorCodes.UNSUPPORTED_OPERATOR;

import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.Set;

/**
 * Condition node that evaluates the authentication state of the customer.
 *
 * <p>Configuration mappings:
 * <ul>
 *   <li>Use {@code Operator.EQUALS} to check if the customer is authenticated (logged in).</li>
 *   <li>Use {@code Operator.NOT_EQUALS} to check if the customer is anonymous (guest checkout).</li>
 * </ul>
 */
public record CustomerAuthenticationStateConditionNode(
    @NotNull(message = "Operator is required")
    Operator operator
) implements ConditionNode {

    public static final String TYPE_NAME = "CUSTOMER_AUTHENTICATION_STATE";

    private static final Set<Operator> ALLOWED = Set.of(Operator.EQUALS, Operator.NOT_EQUALS);

    public static final ConditionNodeSpec SPEC = new ConditionNodeSpec(
        TYPE_NAME,
        "Customer Authentication Status",
        "Evaluates whether the customer is a registered user or an anonymous guest.",
        ALLOWED,
        Map.of(
            Operator.EQUALS, "Is Registered User",
            Operator.NOT_EQUALS, "Is Anonymous Guest"
        ),
        CustomerAuthenticationStateConditionNode.class
    );

    public CustomerAuthenticationStateConditionNode {
        if (!ALLOWED.contains(operator)) {
            throw new InvalidConditionNodeException(
                UNSUPPORTED_OPERATOR,
                String.format("Operator '%s' is not supported by Customer Registration check.", operator)
            );
        }
    }

    @Override
    public String getTypeName() {
        return TYPE_NAME;
    }

    @Override
    public boolean evaluate(EngineWorkspace workspace) {
        boolean isRegistered = !workspace.isCustomerAnonymous();

        return switch (operator) {
            case EQUALS -> isRegistered; // True if signed up
            case NOT_EQUALS -> !isRegistered; // True if anonymous
            default -> false;
        };
    }
}
