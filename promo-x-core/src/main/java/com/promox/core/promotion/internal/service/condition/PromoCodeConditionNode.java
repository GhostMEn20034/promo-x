package com.promox.core.promotion.internal.service.condition;

import com.promox.core.promotion.internal.context.EngineWorkspace;
import com.promox.core.promotion.internal.exception.InvalidConditionNodeException;
import static com.promox.core.promotion.internal.exception.ConditionNodeErrorCodes.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.Set;


public record PromoCodeConditionNode(
    @NotBlank(message = "Promo code cannot be null or blank")
    String code,

    @NotNull(message = "Operator is required")
    Operator operator
) implements ConditionNode {

    public static final String TYPE_NAME = "PROMO_CODE";

    private static final Set<Operator> ALLOWED_OPERATORS = Set.of(Operator.EQUALS, Operator.NOT_EQUALS);

    public static final ConditionNodeSpec SPEC = new ConditionNodeSpec(
        TYPE_NAME,
        "Promo Code Matching",
        "Evaluates whether a specific promotional code has been applied to the current checkout session.",
        ALLOWED_OPERATORS,
        Map.of(
            Operator.EQUALS, "Matches Promo Code",
            Operator.NOT_EQUALS, "Does Not Match Promo Code"
        ),
        PromoCodeConditionNode.class
    );

    public PromoCodeConditionNode {
        if (code == null || code.isBlank()) {
            throw new InvalidConditionNodeException(MISSING_REQUIRED_FIELD, "Promo code value cannot be null or blank.");
        }
        if (!ALLOWED_OPERATORS.contains(operator)) {
            throw new InvalidConditionNodeException(UNSUPPORTED_OPERATOR, "Operator '" + operator + "' is not supported by PromoCodeConditionNode.");
        }
    }

    @Override
    public String getTypeName() {
        return TYPE_NAME;
    }

    @Override
    public boolean evaluate(EngineWorkspace workspace) {
        boolean hasCode = workspace.getPromoCodes().stream()
            .anyMatch(c -> c.equalsIgnoreCase(code));

        return (operator == Operator.EQUALS) == hasCode;
    }
}
