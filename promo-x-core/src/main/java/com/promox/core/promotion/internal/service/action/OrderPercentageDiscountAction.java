package com.promox.core.promotion.internal.service.action;

import com.promox.core.promotion.internal.context.EngineWorkspace;
import com.promox.core.promotion.internal.context.WorkspaceLineItem;
import com.promox.core.promotion.internal.exception.ActionStrategyErrorCodes;
import com.promox.core.promotion.internal.exception.InvalidActionStrategyException;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;


/**
 * Applies a global percentage discount across all line items in the cart.
 * Expects a fractional percentage value (e.g., 0.10 for a 10% discount).
 */
public record OrderPercentageDiscountAction(
    @NotNull(message = "Discount percentage cannot be null")
    @DecimalMin(value = "0.01", message = "Percentage cannot be less than 0.0")
    @DecimalMax(value = "1.0", message = "Percentage cannot be greater than 1.0")
    BigDecimal discountPercentage
) implements ActionStrategy {

    public static final String TYPE_NAME = "ORDER_PERCENTAGE_DISCOUNT";

    public static final ActionStrategySpec SPEC = new ActionStrategySpec(
        TYPE_NAME,
        "Order Percentage Discount",
        """
        Applies a fractional percentage discount (e.g., 0.10 for 10%) globally \
        across all eligible line items in the cart.\
        """,
        OrderPercentageDiscountAction.class
    );

    public OrderPercentageDiscountAction {
        if (discountPercentage == null) {
            throw new InvalidActionStrategyException(ActionStrategyErrorCodes.MISSING_REQUIRED_FIELD, "Discount percentage cannot be null");
        }
        if (discountPercentage.compareTo(BigDecimal.ZERO) < 0 || discountPercentage.compareTo(BigDecimal.ONE) > 0) {
            throw new InvalidActionStrategyException(
                ActionStrategyErrorCodes.INVALID_VALUE_RANGE,
                "Percentage must be from 0.0 to 1"
            );
        }
    }

    @Override
    public String getTypeName() {
        return TYPE_NAME;
    }

    @Override
    public boolean apply(EngineWorkspace workspace) {
        List<WorkspaceLineItem> items = workspace.getLineItems();

        if (items.isEmpty()) {
            return false;
        }

        boolean appliedAnyDiscount = false;

        for (WorkspaceLineItem item : items) {
            BigDecimal lineDiscount = item.getNetPrice()
                .multiply(discountPercentage)
                .setScale(2, RoundingMode.HALF_UP);

            if (lineDiscount.compareTo(BigDecimal.ZERO) > 0) {
                appliedAnyDiscount |= item.addDiscount(lineDiscount);
            }
        }

        return appliedAnyDiscount;
    }
}