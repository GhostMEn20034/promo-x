package com.promox.core.promotion.action;

import com.promox.core.promotion.dto.LineItemRequest;
import com.promox.core.promotion.dto.PromotionEvaluationRequest;
import com.promox.core.promotion.internal.action.FreeShippingAction;
import com.promox.core.promotion.internal.context.EngineWorkspace;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class FreeShippingActionTest {
    private EngineWorkspace buildTestWorkspace(BigDecimal shippingCost) {
        List<LineItemRequest> items = new ArrayList<>();

        items.add(new LineItemRequest(
                "123",
                "123-SKU",
                List.of("Electronics"),
                List.of("Default"),
                1,
                new BigDecimal("12.34")
        ));


        return new EngineWorkspace(new PromotionEvaluationRequest(
                "us-storefront",
                "sess-1",
                "cust-1",
                List.of(),
                items,
                shippingCost
        ));
    }

    @Test
    void apply_ShouldReturnFalse_WhenShippingCostIsAlreadyZero() {
        EngineWorkspace workspace = buildTestWorkspace(BigDecimal.ZERO);

        var shippingAction = new FreeShippingAction();
        assertThat(shippingAction.apply(workspace))
                .isFalse();

        assertThat(workspace.getNetShippingCost())
                .isEqualByComparingTo(BigDecimal.ZERO);

        assertThat(workspace.getShippingCostDiscount())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.01", "15.00", "999.99"})
    @DisplayName("Returns true and fully discounts shipping cost when cost is positive")
    void apply_ShouldReturnTrue_WhenShippingCostIsPositiveDecimal(String shippingCost) {
        var validShippingCost = new BigDecimal(shippingCost);
        EngineWorkspace workspace = buildTestWorkspace(validShippingCost);

        var shippingAction = new FreeShippingAction();
        assertThat(shippingAction.apply(workspace))
                .isTrue();

        assertThat(workspace.getNetShippingCost())
                .isEqualByComparingTo(BigDecimal.ZERO);

        assertThat(workspace.getShippingCostDiscount())
                .isEqualByComparingTo(validShippingCost);

    }
}
