package com.promox.core.promotion.internal.action;

import com.promox.core.promotion.internal.context.EngineWorkspace;
import com.promox.core.promotion.internal.context.WorkspaceLineItem;
import com.promox.core.promotion.internal.exception.InvalidActionStrategyException;

import static com.promox.core.promotion.internal.exception.ActionStrategyErrorCodes.*;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;


public record ProductIdBuyXGetYFreeAction(
        @NotBlank(message = "Target Product ID is required")
        String targetProductId,

        @NotNull(message = "Buy quantity threshold is required")
        @Min(value = 1, message = "Buy quantity must be at least 1")
        Integer buyQuantity,

        @NotNull(message = "Free quantity count is required")
        @Min(value = 1, message = "Free quantity must be at least 1")
        Integer freeQuantity
) implements ActionStrategy {

    public static final String TYPE_NAME = "PRODUCT_ID_BUY_X_GET_X_FREE_ACTION";
    public static final String DISPLAY_NAME = "Buy X Get Y Free (Product ID Specific)";
    private static final String DESC = """
            Applies a recurring Buy X Get Y Free promotion to a unique Product ID. \
            Calculates free items proportionally based on total quantity.\
            """;

    // --- Constructor Boundary Integrity ---
    public ProductIdBuyXGetYFreeAction {
        if (targetProductId == null || targetProductId.isBlank()) {
            throw new InvalidActionStrategyException(
                    MISSING_REQUIRED_FIELD,
                    "Target Product ID cannot be null or blank."
            );
        }
        if (buyQuantity == null || buyQuantity <= 0) {
            throw new InvalidActionStrategyException(
                    INVALID_VALUE_RANGE,
                    "Buy quantity must be greater than zero."
            );
        }
        if (freeQuantity == null || freeQuantity <= 0) {
            throw new InvalidActionStrategyException(
                    INVALID_VALUE_RANGE,
                    "Free quantity must be greater than zero."
            );
        }
    }

    @Override public String getTypeName() { return TYPE_NAME; }
    @Override public String getDisplayName() { return DISPLAY_NAME; }
    @Override public String getDescription() { return DESC; }

    @Override
    public boolean apply(EngineWorkspace workspace) {
        Optional<WorkspaceLineItem> targetItemOpt = workspace.getLineItems().stream()
                .filter(item -> item.getProductId().equalsIgnoreCase(targetProductId))
                .findFirst();

        if (targetItemOpt.isEmpty()) {
            return false;
        }

        WorkspaceLineItem item = targetItemOpt.get();

        // Bypass if the item is already fully discounted
        BigDecimal netPrice = item.getNetPrice();
        if (netPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        // Calculate proportional free units
        int totalQuantity = item.getQuantity();
        int groupSize = this.buyQuantity + this.freeQuantity;
        int completedGroups = totalQuantity / groupSize;
        int freeUnitsToApply = completedGroups * this.freeQuantity;

        if (freeUnitsToApply <= 0) {
            return false; // Customer hasn't added enough units to trigger even 1 free item
        }

        BigDecimal discountAmount = item.getUnitPrice()
                .multiply(BigDecimal.valueOf(freeUnitsToApply))
                .setScale(2, RoundingMode.HALF_UP);

        return item.addDiscount(discountAmount);
    }
}
