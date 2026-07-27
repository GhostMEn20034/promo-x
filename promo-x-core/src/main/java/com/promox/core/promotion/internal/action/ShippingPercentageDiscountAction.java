package com.promox.core.promotion.internal.action;

import com.promox.core.promotion.internal.context.EngineWorkspace;
import com.promox.core.promotion.internal.exception.InvalidActionStrategyException;
import static com.promox.core.promotion.internal.exception.ActionStrategyErrorCodes.*;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record ShippingPercentageDiscountAction(
        @NotNull(message = "Discount percentage is required")
        @DecimalMin(value = "0.00", inclusive = false, message = "Discount rate must be greater than 0.00")
        @DecimalMax(value = "1.00", message = "Discount rate cannot exceed 1.00 (100%)")
        BigDecimal percentage
) implements ActionStrategy {
    public static final String TYPE_NAME = "SHIPPING_PERCENTAGE_DISCOUNT";
    public static final String DISPLAY_NAME = "Shipping Percentage Discount";
    private static final String DESC = """
            Applies a percentage discount rate (0.00 to 1.00) to the flat shipping fee. \
            The action will count as not applied if the shipping cost is already zero.\
            """;

    public ShippingPercentageDiscountAction {
        if (percentage == null) {
            throw new InvalidActionStrategyException(
                    MISSING_REQUIRED_FIELD,
                    "Discount percentage rate cannot be null."
            );
        }
        if (percentage.compareTo(BigDecimal.ZERO) <= 0 || percentage.compareTo(BigDecimal.ONE) > 0) {
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
    public String getDisplayName() {
        return DISPLAY_NAME;
    }

    @Override
    public String getDescription() {
        return DESC;
    }

    @Override
    public boolean apply(EngineWorkspace workspace) {
        BigDecimal currentShippingCost = workspace.getShippingCost();
        if (currentShippingCost.compareTo(BigDecimal.ZERO) == 0) {
            return false;
        }

        BigDecimal discountAmount = currentShippingCost
                .multiply(percentage)
                .setScale(2, RoundingMode.HALF_UP);

        if (discountAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        workspace.addShippingDiscount(discountAmount);
        return true;
    }
}
