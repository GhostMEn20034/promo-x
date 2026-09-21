package com.promox.core.promotion.action;

import com.promox.core.promotion.internal.dto.LineItemRequest;
import com.promox.core.promotion.internal.dto.PromotionEvaluationParams;
import com.promox.core.promotion.internal.service.action.OrderDiscountAction;
import com.promox.core.promotion.internal.context.EngineWorkspace;

import com.promox.core.promotion.internal.context.WorkspaceLineItem;
import com.promox.core.promotion.internal.exception.ActionStrategyErrorCodes;
import com.promox.core.promotion.internal.exception.InvalidActionStrategyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;


public class OrderDiscountActionTest {
    private static final Long STOREFRONT_ID = 123L;

    record ItemSpec(String productId, int quantity, BigDecimal unitPrice) {}

    private EngineWorkspace buildTestWorkspace(List<ItemSpec> specs) {
        List<LineItemRequest> items = new ArrayList<>();
        for (ItemSpec spec : specs) {
            items.add(new LineItemRequest(
                    spec.productId(),
                    spec.productId() + "-SKU",
                    List.of("Electronics"),
                    List.of("Default"),
                    spec.quantity(),
                    spec.unitPrice()
            ));
        }

        return new EngineWorkspace(STOREFRONT_ID, new PromotionEvaluationParams(
                "sess-1",
                "cust-1",
                List.of(),
                items,
                BigDecimal.ZERO
        ));
    }

    // constructor validation (parameterized & individual)
    @Nested
    class ConstructorValidationTests {

        @Test
        @DisplayName("Throws NullPointerException when discountAmount is null")
        void constructor_ShouldThrowNullPointerException_WhenDiscountAmountIsNull() {
            assertThatThrownBy(() -> new OrderDiscountAction(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("Discount amount cannot be null");
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "0.00", "-0.01", "-10.00"})
        @DisplayName("Throws InvalidActionStrategyException when discountAmount is zero or negative")
        void constructor_ShouldThrowInvalidActionStrategyException_WhenDiscountAmountIsZeroOrNegative(String amount) {
            BigDecimal invalidAmount = new BigDecimal(amount);

            assertThatThrownBy(() -> new OrderDiscountAction(invalidAmount))
                    .isInstanceOf(InvalidActionStrategyException.class)
                    .hasMessage("Discount amount must be greater than zero")
                    .extracting("errorCode")
                    .isEqualTo(ActionStrategyErrorCodes.INVALID_VALUE_RANGE);
        }

        @Test
        @DisplayName("Successfully creates instance with valid positive discount amount")
        void constructor_ShouldCreateInstance_WhenDiscountAmountIsValid() {
            var action = new OrderDiscountAction(new BigDecimal("15.00"));
            assertThat(action.discountAmount()).isEqualTo(new BigDecimal("15.00"));
        }
    }

    // execution guards & boundary conditions
    @Nested
    class ApplyGuardTests {

        @Test
        @DisplayName("Returns false when workspace contains no line items")
        void apply_ShouldReturnFalse_WhenWorkspaceContainsNoLineItems() {
            EngineWorkspace workspace = buildTestWorkspace(List.of());

            var action = new OrderDiscountAction(new BigDecimal("10.00"));
            boolean result = action.apply(workspace);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Returns false when total net subtotal is zero")
        void apply_ShouldReturnFalse_WhenTotalNetSubtotalIsZero() {
            EngineWorkspace workspace = buildTestWorkspace(List.of(
                    new ItemSpec("prod-free", 1, BigDecimal.ZERO)
            ));

            var action = new OrderDiscountAction(new BigDecimal("10.00"));
            boolean result = action.apply(workspace);

            assertThat(result).isFalse();
        }
    }

    @Nested
    class ProRataDistributionTests {

        @Test
        @DisplayName("Applies full discount amount to a single line item")
        void apply_ShouldApplyFullDiscount_WhenSingleLineItemPresent() {
            EngineWorkspace workspace = buildTestWorkspace(List.of(
                    new ItemSpec("prod-1", 1, new BigDecimal("100.00"))
            ));

            var action = new OrderDiscountAction(new BigDecimal("20.00"));
            boolean applied = action.apply(workspace);

            assertThat(applied).isTrue();

            WorkspaceLineItem item = workspace.getLineItems().getFirst();
            assertThat(item.getDiscountAmount()).isEqualByComparingTo("20.00");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("proRataScenarios")
        @DisplayName("Distributes discount proportionally based on item weights")
        void apply_ShouldDistributeDiscountProportionally_WhenMultipleLineItemsPresent(
                String scenarioName,
                BigDecimal overallDiscount,
                List<ItemSpec> itemSpecs,
                List<BigDecimal> expectedDiscounts
        ) {
            EngineWorkspace workspace = buildTestWorkspace(itemSpecs);

            var action = new OrderDiscountAction(overallDiscount);
            boolean applied = action.apply(workspace);

            assertThat(applied).isTrue();

            List<WorkspaceLineItem> items = workspace.getLineItems();
            assertThat(items).hasSameSizeAs(expectedDiscounts);

            for (int i = 0; i < items.size(); i++) {
                assertThat(items.get(i).getDiscountAmount())
                        .isEqualByComparingTo(expectedDiscounts.get(i));
            }
        }

        private static Stream<Arguments> proRataScenarios() {
            /*
            The Stream's structure:
            1) Scenario name
            2) Discount amount to be applied
            3) Items where discount must be applied
            4) Expected discounts
            */
            return Stream.of(
                    // 50/50 split: $20.00 discount on 2x $50.00 items -> $10.00 each
                    Arguments.of(
                            "Equal item prices (50/50 split)",
                            new BigDecimal("20.00"),
                            List.of(
                                    new ItemSpec("prod-1", 1, new BigDecimal("50.00")),
                                    new ItemSpec("prod-2", 1, new BigDecimal("50.00"))
                            ),
                            List.of(new BigDecimal("10.00"), new BigDecimal("10.00"))
                    ),
                    // 70/30 split: $10.00 discount on $70.00 and $30.00 items -> $7.00 and $3.00
                    Arguments.of(
                            "Unequal item prices (70/30 split)",
                            new BigDecimal("10.00"),
                            List.of(
                                    new ItemSpec("prod-1", 1, new BigDecimal("70.00")),
                                    new ItemSpec("prod-2", 1, new BigDecimal("30.00"))
                            ),
                            List.of(new BigDecimal("7.00"), new BigDecimal("3.00"))
                    ),
                    // Rounding check: $15.00 discount on $75.00 and $25.00 items
                    Arguments.of(
                            "Decimal calculation with exact rounding",
                            new BigDecimal("15.00"),
                            List.of(
                                    new ItemSpec("prod-1", 1, new BigDecimal("75.00")),
                                    new ItemSpec("prod-2", 1, new BigDecimal("25.00"))
                            ),
                            List.of(new BigDecimal("11.25"), new BigDecimal("3.75"))
                    )
            );
        }
    }
}
