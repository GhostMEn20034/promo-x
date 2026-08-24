package com.promox.core.promotion.internal.service.action;

import com.promox.core.promotion.internal.context.EngineWorkspace;
import com.promox.core.promotion.internal.context.WorkspaceLineItem;
import com.promox.core.promotion.internal.exception.InvalidActionStrategyException;

import static com.promox.core.promotion.internal.exception.ActionStrategyErrorCodes.*;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;


public record ProductSKUBuyXGetYFreeAction(
    @NotBlank(message = "Target SKU is required")
    String targetSku,

    @NotNull(message = "Buy quantity threshold is required")
    @Min(value = 1, message = "Buy quantity must be at least 1")
    Integer buyQuantity,

    @NotNull(message = "Free quantity count is required")
    @Min(value = 1, message = "Free quantity must be at least 1")
    Integer freeQuantity
) implements ActionStrategy {

    public static final String TYPE_NAME = "PRODUCT_SKU_BUY_X_GET_X_FREE_ACTION";

    public static final ActionStrategySpec SPEC = new ActionStrategySpec(
        TYPE_NAME,
        "Buy X Get Y Free (SKU Specific)",
        """
        Applies a recurring Buy X Get Y Free promotion to a specific SKU. \
        Calculates free items proportionally across all matching lines.\
        """,
        ProductSKUBuyXGetYFreeAction.class
    );

    // --- Constructor Boundary Integrity ---
    public ProductSKUBuyXGetYFreeAction {
        if (targetSku == null || targetSku.isBlank()) {
            throw new InvalidActionStrategyException(
                MISSING_REQUIRED_FIELD,
                "Target SKU cannot be null or blank."
            );
        }
        if (buyQuantity == null || buyQuantity <= 0) {
            throw new InvalidActionStrategyException(
                INVALID_VALUE_RANGE,
                "Buy quantity must be greater than zero."
            );
        }
        if (freeQuantity == null) {
            throw new InvalidActionStrategyException(
                MISSING_REQUIRED_FIELD,
                "Free quantity cannot be null."
            );
        }
        if (freeQuantity <= 0) {
            throw new InvalidActionStrategyException(
                INVALID_VALUE_RANGE,
                "Free quantity must be greater than zero."
            );
        }
    }

    @Override
    public String getTypeName() {
        return TYPE_NAME;
    }

    @Override
    public boolean apply(EngineWorkspace workspace) {
        List<WorkspaceLineItem> targets = workspace.getLineItems().stream()
            .filter(item -> item.getProductSku().equalsIgnoreCase(targetSku))
            .toList();

        if (targets.isEmpty()) {
            return false;
        }

        // Calculate the aggregate quantity across all split line items
        int totalSkuQuantity = targets.stream()
            .mapToInt(WorkspaceLineItem::getQuantity)
            .sum();

        // Calculate total free units earned globally for this SKU
        int groupSize = this.buyQuantity + this.freeQuantity;
        int completedGroups = totalSkuQuantity / groupSize;
        int totalFreeUnitsToApply = completedGroups * this.freeQuantity;

        if (totalFreeUnitsToApply <= 0) {
            return false; // Not enough units in cart to satisfy Buy threshold
        }

        int remainingFreeUnitsToApply = totalFreeUnitsToApply;
        boolean appliedAny = false;

        // Distribute the free units across the target line items
        for (WorkspaceLineItem item : targets) {
            if (remainingFreeUnitsToApply <= 0) {
                break; // Fully satisfied the earned free quantity
            }

            BigDecimal netPrice = item.getNetPrice();
            if (netPrice.compareTo(BigDecimal.ZERO) <= 0) {
                continue; // Skip items that are already free from previous promotions
            }

            // Determine how many of the remaining free units can be applied to this specific line
            int unitsToDiscountOnThisLine = Math.min(remainingFreeUnitsToApply, item.getQuantity());
            if (unitsToDiscountOnThisLine <= 0) {
                continue;
            }

            // Calculate discount for this item based on its unit MSRP
            BigDecimal discountAmount = item.getUnitPrice()
                .multiply(BigDecimal.valueOf(unitsToDiscountOnThisLine))
                .setScale(2, RoundingMode.HALF_UP);

            // Safely apply and decrement our pool of free units
            if (item.addDiscount(discountAmount)) {
                appliedAny = true;
                remainingFreeUnitsToApply -= unitsToDiscountOnThisLine;
            }
        }

        return appliedAny;
    }
}