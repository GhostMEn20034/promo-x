package com.promox.core.promotion.internal.action;

import com.promox.core.promotion.internal.context.EngineWorkspace;
import com.promox.core.promotion.internal.context.WorkspaceLineItem;
import com.promox.core.promotion.internal.exception.InvalidActionStrategyException;
import static com.promox.core.promotion.internal.exception.ActionStrategyErrorCodes.*;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;


public record ProductPercentageDiscountAction(
        @NotBlank(message = "Target Product is required")
        String targetProductExternalId,

        @NotNull(message = "Discount percentage rate is required")
        @DecimalMin(value = "0.00", inclusive = false, message = "Discount rate must be greater than 0.00")
        @DecimalMax(value = "1.00", message = "Discount rate cannot exceed 1.00")
        BigDecimal discountPercentage
) implements ActionStrategy {
    public static final String TYPE_NAME = "PRODUCT_EXTERNAL_ID_PERCENTAGE_DISCOUNT_ACTION";
    public static final String DISPLAY_NAME = "Discount on specific Product";
    private static final String DESC = """
            Applies a percentage discount rate (0.00 to 1.00) to all matching Product line items. \
            This action will not apply if the matching items are already fully discounted.\
            """;

    public ProductPercentageDiscountAction {
        if (targetProductExternalId == null || targetProductExternalId.isBlank()) {
            throw new InvalidActionStrategyException(
                    MISSING_REQUIRED_FIELD,
                    "Target Product External ID cannot be null or blank."
            );
        }
        if (discountPercentage == null) {
            throw new InvalidActionStrategyException(
                    MISSING_REQUIRED_FIELD,
                    "Discount percentage cannot be null."
            );
        }
        if (discountPercentage.compareTo(BigDecimal.ZERO) <= 0 || discountPercentage.compareTo(BigDecimal.ONE) > 0) {
            throw new InvalidActionStrategyException(
                    INVALID_VALUE_RANGE,
                    "Discount rate must be between 0.00 (exclusive) and 1.00 (inclusive)."
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

    @Override public Set<String> getProductIdentifiers() { return Set.of(targetProductExternalId); }

    @Override
    public boolean apply(EngineWorkspace workspace) {
        List<WorkspaceLineItem> targets = workspace.getLineItems().stream()
                .filter(item -> item.getProductExternalId().equals(targetProductExternalId))
                .toList();

        if (targets.isEmpty()) {
            return false; // No matching products in the cart
        }

        boolean appliedAny = false;

        for (WorkspaceLineItem item : targets) {
            BigDecimal remainingPrice = item.getNetPrice();
            if (remainingPrice.compareTo(BigDecimal.ZERO) <= 0) {
                continue; // Skip items that are already free
            }

            // Calculate percentage discount based on the remaining net value
            BigDecimal discountAmount = remainingPrice
                    .multiply(discountPercentage)
                    .setScale(2, RoundingMode.HALF_UP);

            appliedAny |= item.addDiscount(discountAmount);
        }

        return appliedAny;
    }
}
