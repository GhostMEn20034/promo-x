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
import java.util.Optional;
import java.util.Set;


public record ProductBuyXGetYFreeAction(
    @NotBlank(message = "Target Product is required")
    String targetProductExternalId,

    @NotNull(message = "Buy quantity threshold is required")
    @Min(value = 1, message = "Buy quantity must be at least 1")
    Integer buyQuantity,

    @NotNull(message = "Free quantity count is required")
    @Min(value = 1, message = "Free quantity must be at least 1")
    Integer freeQuantity
) implements ActionStrategy {

    public static final String TYPE_NAME = "PRODUCT_EXTERNAL_ID_BUY_X_GET_X_FREE_ACTION";

    public static final ActionStrategySpec SPEC = new ActionStrategySpec(
        TYPE_NAME,
        "Buy X Get Y Free (Product Specific)",
        """
        Applies a recurring Buy X Get Y Free promotion to a unique Product. \
        Calculates free items proportionally based on total quantity.\
        """,
        ProductBuyXGetYFreeAction.class
    );

    // --- Constructor Boundary Integrity ---
    public ProductBuyXGetYFreeAction {
        if (targetProductExternalId == null || targetProductExternalId.isBlank()) {
            throw new InvalidActionStrategyException(
                MISSING_REQUIRED_FIELD,
                "Target Product External ID cannot be null or blank."
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

    @Override
    public String getTypeName() {
        return TYPE_NAME;
    }

    @Override
    public Set<String> getProductIdentifiers() {
        return Set.of(targetProductExternalId);
    }

    @Override
    public boolean apply(EngineWorkspace workspace) {
        Optional<WorkspaceLineItem> targetItemOpt = workspace.getLineItems().stream()
            .filter(item -> item.getProductExternalId().equalsIgnoreCase(targetProductExternalId))
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
