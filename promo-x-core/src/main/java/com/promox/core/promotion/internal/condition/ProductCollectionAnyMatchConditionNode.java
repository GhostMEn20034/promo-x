package com.promox.core.promotion.internal.condition;

import com.promox.core.promotion.internal.context.EngineWorkspace;
import com.promox.core.promotion.internal.context.WorkspaceLineItem;
import com.promox.core.promotion.internal.exception.InvalidConditionNodeException;
import static com.promox.core.promotion.internal.exception.ConditionNodeErrorCodes.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;


public record ProductCollectionAnyMatchConditionNode(
        @NotEmpty(message = "Product collections match list cannot be null or empty")
        List<@NotBlank(message = "Individual collections cannot be null or blank") String> collections
) implements ConditionNode {

    public static final String TYPE_NAME = "PRODUCT_COLLECTION_ANY_MATCH";
    public static final String DISPLAY_NAME = "Product Collection List Match";
    private static final String DESC = """
            Evaluates whether any product collection from a specified list is present in the cart line items.\
            """;

    private static final Set<Operator> ALLOWED = Set.of();

    public ProductCollectionAnyMatchConditionNode {
        if (collections == null || collections.isEmpty()) {
            throw new InvalidConditionNodeException(
                    MISSING_REQUIRED_FIELD,
                    "Product collections match list cannot be null or empty."
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
        return Map.of();
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
            return false; // An empty cart cannot satisfy an allowlist collection requirement
        }

        Set<String> targetSet = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        targetSet.addAll(collections);

        // True if any line item's collections array intersects with our target configuration list
        return items.stream()
                .anyMatch(item -> item.getCollections().stream()
                        .anyMatch(targetSet::contains));
    }
}