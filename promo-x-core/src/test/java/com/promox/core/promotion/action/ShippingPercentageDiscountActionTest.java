package com.promox.core.promotion.action;

import com.promox.core.promotion.internal.dto.LineItemRequest;
import com.promox.core.promotion.internal.dto.PromotionEvaluationParams;
import com.promox.core.promotion.internal.service.action.ShippingPercentageDiscountAction;
import com.promox.core.promotion.internal.context.EngineWorkspace;
import com.promox.core.promotion.internal.exception.InvalidActionStrategyException;

import static com.promox.core.promotion.internal.exception.ActionStrategyErrorCodes.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;


public class ShippingPercentageDiscountActionTest {
    private static final Long STOREFRONT_ID = 123L;

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


        return new EngineWorkspace(STOREFRONT_ID, new PromotionEvaluationParams(
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

        var shippingAction = new ShippingPercentageDiscountAction(new BigDecimal("0.15"));
        assertThat(shippingAction.apply(workspace))
                .isFalse();

        assertThat(workspace.getNetShippingCost())
                .isEqualByComparingTo(BigDecimal.ZERO);

        assertThat(workspace.getShippingCostDiscount())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void constructor_ShouldThrowInvalidActionStrategyException_WhenPercentageIsNull() {
        assertThatThrownBy(() -> new ShippingPercentageDiscountAction(null))
                .isInstanceOf(InvalidActionStrategyException.class)
                .extracting("errorCode")
                .isEqualTo(MISSING_REQUIRED_FIELD);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-50.00", "-0.5", "-0.1", "1.1", "1.5", "50"})
    void constructor_ShouldThrowInvalidActionStrategyException_WhenPercentageIsOutOfRange(
            String shippingPercentageDiscount
    ) {
        var validatedShippingPercentageDiscount = new BigDecimal(shippingPercentageDiscount);

        assertThatThrownBy(() -> new ShippingPercentageDiscountAction(validatedShippingPercentageDiscount))
                .isInstanceOf(InvalidActionStrategyException.class)
                .extracting("errorCode")
                .isEqualTo(INVALID_VALUE_RANGE);
    }

    @Test
    @DisplayName("Returns false when calculated percentage discount rounds to 0.00")
    void apply_ShouldReturnFalse_WhenCalculatedDiscountRoundsToZero() {
        // Arrange: 1% of $0.01 = $0.0001, which rounds down to $0.00
        EngineWorkspace workspace = buildTestWorkspace(new BigDecimal("0.01"));
        var action = new ShippingPercentageDiscountAction(new BigDecimal("0.01"));

        // Act
        boolean applied = action.apply(workspace);

        // Assert
        assertThat(applied).isFalse();
        assertThat(workspace.getShippingCostDiscount()).isEqualByComparingTo("0.00");
        assertThat(workspace.getNetShippingCost()).isEqualByComparingTo("0.01");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "100% discount (full free shipping), 1.00, 15.00, 15.00, 0.00",
            "50% discount on standard shipping,  0.50, 10.00, 5.00,  5.00",
            "15% discount with HALF_UP rounding, 0.15, 10.33, 1.55,  8.78"
    })
    void apply_ShouldCalculateDiscountAndNetShippingCorrectly(
            String scenario,
            String percentage,
            String shippingCost,
            String expectedDiscount,
            String expectedNetShipping
    ) {
        EngineWorkspace workspace = buildTestWorkspace(new BigDecimal(shippingCost));
        var action = new ShippingPercentageDiscountAction(new BigDecimal(percentage));

        boolean applied = action.apply(workspace);

        assertThat(applied).isTrue();
        assertThat(workspace.getShippingCostDiscount())
                .isEqualByComparingTo(new BigDecimal(expectedDiscount));
        assertThat(workspace.getNetShippingCost())
                .isEqualByComparingTo(new BigDecimal(expectedNetShipping));
    }

}
