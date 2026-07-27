package com.promox.core.promotion.internal.condition;

import com.promox.core.promotion.internal.context.EngineWorkspace;
import com.promox.core.promotion.internal.exception.InvalidConditionNodeException;
import static com.promox.core.promotion.internal.exception.ConditionNodeErrorCodes.*;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.Set;
import java.math.BigDecimal;


public record SubtotalConditionNode(
        @NotNull(message = "Threshold cannot be null")
        @DecimalMin(value = "0.0", message = "Threshold cannot be negative")
        BigDecimal threshold,

        @NotNull(message = "Operator is required")
        Operator operator,

        @NotNull(message = "Subtotal type (GROSS or NET) is required")
        SubtotalType subtotalType
) implements ConditionNode {

    public static final String TYPE_NAME = "SUBTOTAL";
    public static final String DISPLAY_NAME = "Cart Subtotal";
    private static final String DESC = """
            Evaluates whether the items subtotal in the cart matches the defined threshold value.\
            """;

    private static final Set<Operator> ALLOWED_OPERATORS = Set.of(
            Operator.GREATER_THAN_OR_EQUAL,
            Operator.EQUALS,
            Operator.NOT_EQUALS
    );

    public SubtotalConditionNode {
        if (threshold == null) {
            throw new InvalidConditionNodeException(MISSING_REQUIRED_FIELD, "Subtotal threshold cannot be null.");
        }
        if (threshold.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidConditionNodeException(INVALID_VALUE_RANGE, "Subtotal threshold cannot be negative.");
        }
        if (!ALLOWED_OPERATORS.contains(operator)) {
            throw new InvalidConditionNodeException(UNSUPPORTED_OPERATOR, "Operator '" + operator + "' is not supported by SubtotalConditionNode.");
        }
    }

    @Override
    public String getTypeName() {
        return TYPE_NAME;
    }

    @Override
    public String getDisplayName() {
        return DISPLAY_NAME;
    }

    @Override
    public Map<Operator, String> getOperatorsDisplayName() {
        return Map.of(
                Operator.GREATER_THAN_OR_EQUAL, "Greater Than or Equal To",
                Operator.EQUALS, "Equals",
                Operator.NOT_EQUALS, "Does Not Equal"
        );
    }

    @Override
    public String getDescription() {
        return DESC;
    }

    @Override
    public Set<Operator> getSupportedOperators() {
        return ALLOWED_OPERATORS;
    }

    @Override
    public boolean evaluate(EngineWorkspace workspace) {
        BigDecimal calculatedSubtotal = switch (this.subtotalType) {
            case GROSS -> workspace.getItemsSubtotal();
            case NET -> workspace.getItemsNetSubtotal();
        };

        int comparison = calculatedSubtotal.compareTo(threshold);
        return switch (operator) {
            case GREATER_THAN_OR_EQUAL -> comparison >= 0;
            case EQUALS -> comparison == 0;
            case NOT_EQUALS -> comparison != 0;
        };
    }
}
