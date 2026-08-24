package com.promox.core.promotion.internal.service.action;

import com.promox.core.promotion.internal.context.EngineWorkspace;
import com.promox.core.promotion.internal.context.WorkspaceLineItem;
import com.promox.core.promotion.internal.exception.InvalidActionStrategyException;
import static com.promox.core.promotion.internal.exception.ActionStrategyErrorCodes.*;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;


public record ProductSKUPercentageDiscountAction(
    @NotNull(message = "Target SKU percentage cannot be null")
    String targetSku,

    @NotNull(message = "Discount percentage cannot be null")
    @DecimalMin(value = "0.00", inclusive = false, message = "Discount rate must be greater than 0.00")
    @DecimalMax(value = "1.00", message = "Discount rate cannot exceed 1.00")
    BigDecimal discountPercentage
) implements ActionStrategy {

    public static final String TYPE_NAME = "PRODUCT_PERCENTAGE_DISCOUNT_ACTION";

    public static final ActionStrategySpec SPEC = new ActionStrategySpec(
        TYPE_NAME,
        "Discount on the specific product (SKU)",
        """
        Applies a percentage discount rate (0.00 to 1.00) to the line item. \
        The action will count as not applied if the line item total is already zero.\
        """,
        ProductSKUPercentageDiscountAction.class
    );

    public ProductSKUPercentageDiscountAction {
        if (targetSku == null || targetSku.isBlank()) {
            throw new InvalidActionStrategyException(
                MISSING_REQUIRED_FIELD,
                "Target SKU cannot be null or blank."
            );
        }
        if (discountPercentage == null) {
            throw new InvalidActionStrategyException(
                MISSING_REQUIRED_FIELD,
                "Discount percentage rate cannot be null."
            );
        }
        if (discountPercentage.compareTo(BigDecimal.ZERO) <= 0 || discountPercentage.compareTo(BigDecimal.ONE) > 0) {
            throw new InvalidActionStrategyException(
                INVALID_VALUE_RANGE,
                "Discount rate must be strictly between 0.00 (exclusive) and 1.00 (inclusive)."
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

        boolean appliedAny = false;

        for (WorkspaceLineItem item : targets) {
            BigDecimal remainingPrice = item.getNetPrice();
            if (remainingPrice.compareTo(BigDecimal.ZERO) <= 0) {
                continue; // Skip line items that are already free
            }

            // Calculate percentage discount based on the remaining net value
            BigDecimal discountAmount = remainingPrice
                .multiply(discountPercentage)
                .setScale(2, RoundingMode.HALF_UP);

            // Attempt to apply and track if state actually mutated
            if (item.addDiscount(discountAmount)) {
                appliedAny = true;
            }
        }

        return appliedAny;
    }
}