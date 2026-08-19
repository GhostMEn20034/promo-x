package com.promox.core.promotion.internal.condition;

import com.promox.core.promotion.internal.context.EngineWorkspace;
import com.promox.core.promotion.internal.context.WorkspaceLineItem;
import com.promox.core.promotion.internal.exception.InvalidConditionNodeException;
import static com.promox.core.promotion.internal.exception.ConditionNodeErrorCodes.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;


public record ProductAnyMatchConditionNode(
        @NotEmpty(message = "Target Product list cannot be null or empty")
        Set<@NotBlank(message = "Individual Product cannot be null or blank") String> productExternalIds,

        @NotNull(message = "Operator is required")
        Operator operator
) implements ConditionNode {
    public static final String TYPE_NAME = "PRODUCT_EXTERNAL_ID_ANY_MATCH";
    public static final String DISPLAY_NAME = "Product List Match";
    private static final String DESC = """
            Evaluates whether any product from a specified list is present or absent in the cart line items.\
            """;

    private static final Set<Operator> ALLOWED = Set.of(Operator.EQUALS, Operator.NOT_EQUALS);

    public ProductAnyMatchConditionNode {
        if (productExternalIds == null || productExternalIds.isEmpty()) {
            throw new InvalidConditionNodeException(
                    MISSING_REQUIRED_FIELD,
                    "Target Product External ID list cannot be null or empty."
            );
        }
        if (!ALLOWED.contains(operator)) {
            throw new InvalidConditionNodeException(
                    UNSUPPORTED_OPERATOR,
                    String.format("Operator '%s' is not supported by Product External ID list matching.", operator)
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
    public Set<String> getProductIdentifiers() {
        return productExternalIds;
    }

    @Override
    public boolean evaluate(EngineWorkspace workspace) {
        List<WorkspaceLineItem> items = workspace.getLineItems();
        if (items.isEmpty()) {
            return operator == Operator.NOT_EQUALS;
        }

        return switch (operator) {
            case EQUALS -> items.stream().anyMatch(item -> productExternalIds.contains(item.getProductExternalId()));
            case NOT_EQUALS -> items.stream().noneMatch(item -> productExternalIds.contains(item.getProductExternalId()));
            default -> false;
        };
    }
}
