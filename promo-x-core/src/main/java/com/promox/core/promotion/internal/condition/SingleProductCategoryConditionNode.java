package com.promox.core.promotion.internal.condition;

import com.promox.core.promotion.internal.context.EngineWorkspace;
import com.promox.core.promotion.internal.context.WorkspaceLineItem;
import com.promox.core.promotion.internal.exception.InvalidConditionNodeException;
import static com.promox.core.promotion.internal.exception.ConditionNodeErrorCodes.*;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;


public record SingleProductCategoryConditionNode(
        @NotBlank(message = "Target Product category cannot be null or blank")
        String category,

        @NotNull(message = "Operator is required")
        Operator operator
) implements ConditionNode {
    public static final String TYPE_NAME = "SINGLE_PRODUCT_CATEGORY_MATCH";
    public static final String DISPLAY_NAME = "Single Product Category Match";
    private static final String DESC = """
            Evaluates whether a specific product category is present or absent in the cart line items.\
            """;

    private static final Set<Operator> ALLOWED = Set.of(Operator.EQUALS, Operator.NOT_EQUALS);

    public SingleProductCategoryConditionNode {
        if (category == null || category.isBlank()) {
            throw new InvalidConditionNodeException(
                    MISSING_REQUIRED_FIELD,
                    "Target Product category cannot be null or blank."
            );
        }
        if (!ALLOWED.contains(operator)) {
            throw new InvalidConditionNodeException(
                    UNSUPPORTED_OPERATOR,
                    String.format("Operator '%s' is not supported by Single Product category matching.", operator)
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
                Operator.EQUALS, "Contains Category",
                Operator.NOT_EQUALS, "Does Not Contain Category"
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
            case EQUALS -> items.stream().anyMatch(item -> item.getCategories().contains(category));
            case NOT_EQUALS -> items.stream().noneMatch(item -> item.getCategories().contains(category));
            default -> false;
        };
    }
}
