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


public record SingleProductCollectionConditionNode(
    @NotBlank(message = "Target Product collection cannot be null or blank")
    String collection,

    @NotNull(message = "Operator is required")
    Operator operator
) implements ConditionNode {

    public static final String TYPE_NAME = "SINGLE_PRODUCT_COLLECTION_MATCH";

    private static final Set<Operator> ALLOWED = Set.of(Operator.EQUALS, Operator.NOT_EQUALS);

    public static final ConditionNodeSpec SPEC = new ConditionNodeSpec(
        TYPE_NAME,
        "Single Product Collection Match",
        "Evaluates whether a specific product collection is present or absent in the cart line items.",
        ALLOWED,
        Map.of(
            Operator.EQUALS, "Contains Collection",
            Operator.NOT_EQUALS, "Does Not Contain Collection"
        ),
        SingleProductCollectionConditionNode.class
    );

    public SingleProductCollectionConditionNode {
        if (collection == null || collection.isBlank()) {
            throw new InvalidConditionNodeException(
                MISSING_REQUIRED_FIELD,
                "Target Product collection cannot be null or blank."
            );
        }
        if (operator == null) {
            throw new InvalidConditionNodeException(MISSING_REQUIRED_FIELD, "Operator is required.");
        }
        if (!ALLOWED.contains(operator)) {
            throw new InvalidConditionNodeException(
                UNSUPPORTED_OPERATOR,
                String.format("Operator '%s' is not supported by Single Product collection matching.", operator)
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
            return operator == Operator.NOT_EQUALS; // An empty cart contains zero excluded collections
        }

        return switch (operator) {
            // True if at least one item belongs to the targeted collection
            case EQUALS -> items.stream()
                .filter(item -> item.getCollections() != null)
                .anyMatch(item -> item.getCollections().stream()
                    .anyMatch(collection::equalsIgnoreCase));

            // True only if absolutely NO items belong to the targeted collection
            case NOT_EQUALS -> items.stream()
                .filter(item -> item.getCollections() != null)
                .noneMatch(item -> item.getCollections().stream()
                    .anyMatch(collection::equalsIgnoreCase));

            default -> false;
        };
    }
}
