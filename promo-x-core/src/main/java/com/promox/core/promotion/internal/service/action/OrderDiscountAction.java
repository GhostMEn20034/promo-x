package com.promox.core.promotion.internal.service.action;

import com.promox.core.promotion.internal.context.EngineWorkspace;
import com.promox.core.promotion.internal.context.WorkspaceLineItem;
import com.promox.core.promotion.internal.exception.ActionStrategyErrorCodes;
import com.promox.core.promotion.internal.exception.InvalidActionStrategyException;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;


public record OrderDiscountAction(
        @NotNull(message = "Discount amount cannot be null")
        @DecimalMin(value = "0.01", message = "Discount amount must be greater than zero")
        BigDecimal discountAmount
) implements ActionStrategy {
    public static final String TYPE_NAME = "ORDER_FLAT_DISCOUNT";
    public static final String DISPLAY_NAME = "Order Flat Currency Discount";
    private static final String DESC = """
            Applies a fixed currency discount across all eligible line items in the cart, \
            proportionally distributing the deduction based on each line item's subtotal weight.\
            """;

    public OrderDiscountAction {
        Objects.requireNonNull(discountAmount, "Discount amount cannot be null");
        if (discountAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidActionStrategyException(
                    ActionStrategyErrorCodes.INVALID_VALUE_RANGE,
                    "Discount amount must be greater than zero"
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
        List<WorkspaceLineItem> items = workspace.getLineItems();
        if (items.isEmpty()) return false;

        // Pro-rata distribution across lines to maintain clean subtotaling
        BigDecimal totalSubtotal = workspace.getItemsNetSubtotal();
        if (totalSubtotal.compareTo(BigDecimal.ZERO) == 0) return false;

        for (WorkspaceLineItem item : items) {
            BigDecimal itemWeight = item.getTotalPrice().divide(totalSubtotal, 4, RoundingMode.HALF_UP);
            BigDecimal share = discountAmount.multiply(itemWeight).setScale(2, RoundingMode.HALF_UP);
            item.addDiscount(share);
        }
        return true;
    }
}
