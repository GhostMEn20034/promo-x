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


public record ProductSkuAnyMatchConditionNode(
        @NotEmpty(message = "Target SKU list cannot be null or empty")
        List<@NotBlank(message = "Individual SKUs cannot be null or blank") String> skus,

        @NotNull(message = "Operator is required")
        Operator operator
) implements ConditionNode {
    public static final String TYPE_NAME = "PRODUCT_SKU_ANY_MATCH";
    public static final String DISPLAY_NAME = "Product SKU List Match";
    private static final String DESC = """
            Evaluates whether any product SKU from a specified list is present or absent in the cart line items.\
            """;

    private static final Set<Operator> ALLOWED = Set.of(Operator.EQUALS, Operator.NOT_EQUALS);

    public ProductSkuAnyMatchConditionNode {
        if (skus == null || skus.isEmpty()) {
            throw new InvalidConditionNodeException(
                    MISSING_REQUIRED_FIELD,
                    "Target SKU list cannot be null or empty."
            );
        }
        if (!ALLOWED.contains(operator)) {
            throw new InvalidConditionNodeException(
                    UNSUPPORTED_OPERATOR,
                    String.format("Operator '%s' is not supported by SKU list matching.", operator)
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
                Operator.EQUALS, "Contains Any SKU From List",
                Operator.NOT_EQUALS, "Does Not Contain Any SKU From List"
        );
    }

    @Override
    public String getDescription() {
        return DESC;
    }

    @Override
    public Set<Operator> getSupportedOperators() {
        return ALLOWED;
    }

    @Override
    public boolean evaluate(EngineWorkspace workspace) {
        List<WorkspaceLineItem> items = workspace.getLineItems();
        if (items.isEmpty()) {
            return operator == Operator.NOT_EQUALS;
        }

        return switch (operator) {
            case EQUALS -> items.stream().anyMatch(item -> skus.contains(item.getProductSku()));
            case NOT_EQUALS -> items.stream().noneMatch(item -> skus.contains(item.getProductSku()));
            default -> false;
        };
    }
}
