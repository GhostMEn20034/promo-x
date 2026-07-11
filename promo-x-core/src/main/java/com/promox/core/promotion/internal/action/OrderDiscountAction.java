package com.promox.core.promotion.internal.action;

import com.promox.core.promotion.internal.context.EngineWorkspace;
import com.promox.core.promotion.internal.context.WorkspaceLineItem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public record OrderDiscountAction(BigDecimal discountAmount) implements ActionStrategy {
    @Override
    public void apply(EngineWorkspace workspace) {
        List<WorkspaceLineItem> items = workspace.getLineItems();
        if (items.isEmpty()) return;

        // Pro-rata distribution across lines to maintain clean subtotaling
        BigDecimal totalSubtotal = workspace.getItemsSubtotal();
        if (totalSubtotal.compareTo(BigDecimal.ZERO) == 0) return;

        for (WorkspaceLineItem item : items) {
            BigDecimal itemWeight = item.getTotalPrice().divide(totalSubtotal, 4, RoundingMode.HALF_UP);
            BigDecimal share = discountAmount.multiply(itemWeight).setScale(2, RoundingMode.HALF_UP);
            item.addDiscount(share);
        }
    }
}
