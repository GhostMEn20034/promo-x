package com.promox.core.promotion.internal.action;

import com.promox.core.promotion.internal.context.EngineWorkspace;
import com.promox.core.promotion.internal.context.WorkspaceLineItem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;


/**
 * Applies a global percentage discount across all line items in the cart.
 * Expects a fractional percentage value (e.g., 0.10 for a 10% discount).
 */
public record OrderPercentageDiscountAction(
        BigDecimal discountPercentage
) implements ActionStrategy {

    @Override
    public void apply(EngineWorkspace workspace) {
        List<WorkspaceLineItem> items = workspace.getLineItems();

        for (WorkspaceLineItem item : items) {
            // Calculate the discount for this specific line item: lineTotalPrice * percentage
            BigDecimal lineDiscount = item.getTotalPrice()
                    .multiply(discountPercentage)
                    .setScale(2, RoundingMode.HALF_UP);

            // Add the calculated amount directly to the line item's running discount state
            item.addDiscount(lineDiscount);
        }
    }
}
