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
import java.util.TreeSet;


public record ProductCategoryAnyMatchConditionNode(
        @NotEmpty(message = "Product categories match list cannot be null or empty")
        List<@NotBlank(message = "Individual categories cannot be null or blank") String> categories,

        @NotNull(message = "Operator is required")
        Operator operator
) implements ConditionNode {
    public static final String TYPE_NAME = "PRODUCT_CATEGORY_ANY_MATCH";
    public static final String DISPLAY_NAME = "Product Category List Match";
    private static final String DESC = """
            Evaluates whether any product category from a specified list is present in the cart line items.\
            """;

    private static final Set<Operator> ALLOWED = Set.of(Operator.EQUALS);

    public ProductCategoryAnyMatchConditionNode {
        if (categories == null || categories.isEmpty()) {
            throw new InvalidConditionNodeException(
                    MISSING_REQUIRED_FIELD,
                    "Product categories match list cannot be null or empty."
            );
        }
        if (!ALLOWED.contains(operator)) {
            throw new InvalidConditionNodeException(
                    UNSUPPORTED_OPERATOR,
                    String.format("Operator '%s' is not supported by Category Any-Match logic. Only EQUALS is allowed.", operator)
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
                Operator.EQUALS, "Contains Any Category From List"
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
            return false; // An empty cart cannot fulfill an allowlist category requirement
        }

        Set<String> targetSet = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        targetSet.addAll(categories);

        // True if any line item's category collection intersects with our target categories list
        return items.stream()
                .anyMatch(item -> item.getCategories().stream()
                        .anyMatch(targetSet::contains));
    }
}