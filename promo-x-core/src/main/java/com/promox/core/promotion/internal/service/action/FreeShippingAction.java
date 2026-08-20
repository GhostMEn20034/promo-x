package com.promox.core.promotion.internal.service.action;

import com.promox.core.promotion.internal.context.EngineWorkspace;

import java.math.BigDecimal;

public record FreeShippingAction() implements ActionStrategy {
    public static final String TYPE_NAME = "FREE_SHIPPING_ACTION";
    public static final String DISPLAY_NAME = "Free Shipping";
    private static final String DESC = """
            Waives the flat shipping fee on the order by setting the shipping cost to zero.\
            The action will count as not applied if the shipping cost is already zero.\
            """;

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

        workspace.addShippingDiscount(currentShippingCost);
        return true;
    }
}
