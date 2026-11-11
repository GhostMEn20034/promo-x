package com.promox.core.promotion.internal.service.condition;

import com.promox.core.promotion.internal.context.EngineWorkspace;
import com.promox.core.promotion.internal.context.WorkspaceLineItem;
import com.promox.core.promotion.internal.exception.InvalidConditionNodeException;
import static com.promox.core.promotion.internal.exception.ConditionNodeErrorCodes.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;


public record SingleProductSkuConditionNode(
    @NotBlank(message = "Target SKU cannot be null or blank")
    String sku,

    @NotNull(message = "Operator is required")
    Operator operator
) implements ConditionNode {

    public static final String TYPE_NAME = "SINGLE_PRODUCT_SKU_MATCH";

    private static final Set<Operator> ALLOWED = Set.of(Operator.EQUALS, Operator.NOT_EQUALS);

    public static final ConditionNodeSpec SPEC = new ConditionNodeSpec(
        TYPE_NAME,
        "Single Product SKU Match",
        "Evaluates whether a specific product SKU is present or absent in the cart line items.",
        ALLOWED,
        Map.of(
            Operator.EQUALS, "Contains SKU",
            Operator.NOT_EQUALS, "Does Not Contain SKU"
        ),
        SingleProductSkuConditionNode.class
    );

    public SingleProductSkuConditionNode {
        if (sku == null || sku.isBlank()) {
            throw new InvalidConditionNodeException(
                MISSING_REQUIRED_FIELD,
                "Target SKU cannot be null or blank."
            );
        }
        if (operator == null) {
            throw new InvalidConditionNodeException(MISSING_REQUIRED_FIELD, "Operator is required.");
        }
        if (!ALLOWED.contains(operator)) {
            throw new InvalidConditionNodeException(
                UNSUPPORTED_OPERATOR,
                String.format("Operator '%s' is not supported by Single SKU matching.", operator)
            );
        }
    }

    @Override
    public String getTypeName() {
        return TYPE_NAME;
    }

    @Override
    public boolean evaluate(EngineWorkspace workspace) {
        List<WorkspaceLineItem> items = workspace.getLineItems();
        if (items.isEmpty()) {
            return operator == Operator.NOT_EQUALS;
        }

        return switch (operator) {
            case EQUALS -> items.stream().anyMatch(item -> sku.equalsIgnoreCase(item.getProductSku()));
            case NOT_EQUALS -> items.stream().noneMatch(item -> sku.equalsIgnoreCase(item.getProductSku()));
            default -> false;
        };
    }
}