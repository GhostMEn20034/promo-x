package com.promox.core.promotion.internal.condition;

import com.promox.core.promotion.internal.context.EngineWorkspace;
import com.promox.core.promotion.internal.context.WorkspaceLineItem;
import com.promox.core.promotion.internal.exception.InvalidConditionNodeException;
import static com.promox.core.promotion.internal.exception.PromotionErrorCodes.*;


import java.util.List;
import java.util.Map;
import java.util.Set;


public record ProductIdAnyMatchConditionNode(
        List<String> productIds,
        Operator operator
) implements ConditionNode {
    public static final String TYPE_NAME = "PRODUCT_ID_ANY_MATCH";
    public static final String DISPLAY_NAME = "Product ID List Match";
    private static final String DESC = """
            Evaluates whether any product ID from a specified list is present or absent in the cart line items.\
            """;

    private static final Set<Operator> ALLOWED = Set.of(Operator.EQUALS, Operator.NOT_EQUALS);

    public ProductIdAnyMatchConditionNode {
        if (productIds == null || productIds.isEmpty()) {
            throw new InvalidConditionNodeException(
                    MISSING_REQUIRED_FIELD,
                    "Target Product ID list cannot be null or empty."
            );
        }
        if (!ALLOWED.contains(operator)) {
            throw new InvalidConditionNodeException(
                    UNSUPPORTED_OPERATOR,
                    String.format("Operator '%s' is not supported by Product ID list matching.", operator)
            );
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
                Operator.EQUALS, "Contains Any Product From List",
                Operator.NOT_EQUALS, "Does Not Contain Any Product From List"
        );
    }

    @Override
    public Set<Operator> getSupportedOperators() {
        return ALLOWED;
    }

    @Override
    public String getDescription() {
        return DESC;
    }

    @Override
    public boolean evaluate(EngineWorkspace workspace) {
        List<WorkspaceLineItem> items = workspace.getLineItems();
        if (items.isEmpty()) {
            return operator == Operator.NOT_EQUALS;
        }

        return switch (operator) {
            case EQUALS -> items.stream().anyMatch(item -> productIds.contains(item.getProductId()));
            case NOT_EQUALS -> items.stream().noneMatch(item -> productIds.contains(item.getProductId()));
            default -> false;
        };
    }
}
