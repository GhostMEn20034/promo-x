package com.promox.core.promotion.internal.service.condition;

import com.promox.core.promotion.internal.context.EngineWorkspace;
import com.promox.core.promotion.internal.context.WorkspaceLineItem;
import com.promox.core.promotion.internal.exception.InvalidConditionNodeException;
import static com.promox.core.promotion.internal.exception.ConditionNodeErrorCodes.*;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;


public record ProductQuantityConditionNode(
    @NotBlank(message = "Target Product cannot be null or blank")
    String productExternalId,

    @Min(value = 1, message = "Required quantity must be greater than zero")
    int requiredQuantity,

    @NotNull(message = "Operator is required")
    Operator operator
) implements ConditionNode {

    public static final String TYPE_NAME = "PRODUCT_EXTERNAL_ID_QUANTITY";

    private static final Set<Operator> ALLOWED = Set.of(Operator.EQUALS, Operator.GREATER_THAN_OR_EQUAL);

    public static final ConditionNodeSpec SPEC = new ConditionNodeSpec(
        TYPE_NAME,
        "Product Quantity",
        "Evaluates whether the combined quantity of line items matching a specific product meets the required threshold.",
        ALLOWED,
        Map.of(
            Operator.EQUALS, "Quantity Equals",
            Operator.GREATER_THAN_OR_EQUAL, "Quantity Greater Than or Equal To"
        ),
        ProductQuantityConditionNode.class
    );

    public ProductQuantityConditionNode {
        if (productExternalId == null || productExternalId.isBlank()) {
            throw new InvalidConditionNodeException(
                MISSING_REQUIRED_FIELD,
                "Target Product External ID cannot be null or blank."
            );
        }
        if (requiredQuantity < 1) {
            throw new InvalidConditionNodeException(
                INVALID_VALUE_RANGE,
                "Required quantity must be greater than zero."
            );
        }
        if (!ALLOWED.contains(operator)) {
            throw new InvalidConditionNodeException(
                UNSUPPORTED_OPERATOR,
                String.format("Operator '%s' is not supported by Product External ID quantity validation.", operator)
            );
        }
    }

    @Override
    public String getTypeName() {
        return TYPE_NAME;
    }

    @Override
    public Set<String> getProductIdentifiers() {
        return Set.of(productExternalId);
    }

    @Override
    public boolean evaluate(EngineWorkspace workspace) {
        List<WorkspaceLineItem> items = workspace.getLineItems();
        if (items.isEmpty()) {
            return false;
        }

        // Sum quantities across lines matching the broader parent Product ID
        int actualQuantity = items.stream()
            .filter(item -> productExternalId.equals(item.getProductExternalId()))
            .mapToInt(WorkspaceLineItem::getQuantity)
            .sum();

        return switch (operator) {
            case GREATER_THAN_OR_EQUAL -> actualQuantity >= requiredQuantity;
            case EQUALS -> actualQuantity == requiredQuantity;
            default -> false;
        };
    }
}