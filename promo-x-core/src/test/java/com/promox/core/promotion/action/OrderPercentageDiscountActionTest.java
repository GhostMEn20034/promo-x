package com.promox.core.promotion.action;

import com.promox.core.promotion.dto.LineItemRequest;
import com.promox.core.promotion.dto.PromotionEvaluationRequest;
import com.promox.core.promotion.internal.action.OrderPercentageDiscountAction;
import com.promox.core.promotion.internal.context.EngineWorkspace;
import com.promox.core.promotion.internal.context.WorkspaceLineItem;
import com.promox.core.promotion.internal.exception.InvalidActionStrategyException;
import static com.promox.core.promotion.internal.exception.ActionStrategyErrorCodes.*;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;


public class OrderPercentageDiscountActionTest {
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

        return new EngineWorkspace(new PromotionEvaluationRequest(
                123L,
                "sess-1",
                "cust-1",
                List.of(),
                items,
                BigDecimal.ZERO
        ));
    }

    @Nested
    class ConstructorValidationTests {

        @Test
        void validate_ShouldThrowNullPoException_WhenDiscountPercentageIsNull() {
            assertThatThrownBy(() -> new OrderPercentageDiscountAction(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void validate_ShouldThrowInvalidActionStrategyException_WhenDiscountPercentageIsOutOfRange() {
            assertThatThrownBy(() -> new OrderPercentageDiscountAction(new BigDecimal("-1.0")))
                    .isInstanceOf(InvalidActionStrategyException.class)
                    .extracting("errorCode")
                    .isEqualTo(INVALID_VALUE_RANGE);

            assertThatThrownBy(() -> new OrderPercentageDiscountAction(new BigDecimal("1.1")))
                    .isInstanceOf(InvalidActionStrategyException.class)
                    .extracting("errorCode")
                    .isEqualTo(INVALID_VALUE_RANGE);
        }

        @ParameterizedTest
        @ValueSource(strings = {"0.0", "0.15", "1.0"})
        void validate_ShouldCreateInstance_WhenDiscountPercentageIsInRange(String percentage) {
            BigDecimal validPercentage = new BigDecimal(percentage);

            var action = new OrderPercentageDiscountAction(validPercentage);

            assertThat(action.discountPercentage()).isEqualTo(validPercentage);
        }
    }

    @Nested
    class ApplyGuardTests {

        @Test
        @DisplayName("Returns false when workspace contains no line items")
        void apply_ShouldReturnFalse_WhenWorkspaceContainsNoLineItems() {
            EngineWorkspace workspace = buildTestWorkspace(List.of());

            var action = new OrderPercentageDiscountAction(new BigDecimal("0.1"));
            boolean result = action.apply(workspace);

            Assertions.assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Returns false when total net subtotal is zero")
        void apply_ShouldReturnFalse_WhenTotalNetSubtotalIsZero() {
            EngineWorkspace workspace = buildTestWorkspace(List.of(
                    new ItemSpec("prod-free", 1, BigDecimal.ZERO)
            ));

            var action = new OrderPercentageDiscountAction(new BigDecimal("0.50"));
            boolean result = action.apply(workspace);

            Assertions.assertThat(result).isFalse();
        }
    }


    @Nested
    class PercentageDiscountDistributionTests {

        @Test
        @DisplayName("Applies correct percentage discount to a single line item")
        void apply_ShouldApplyPercentageDiscount_WhenSingleLineItemPresent() {
            EngineWorkspace workspace = buildTestWorkspace(List.of(
                    new ItemSpec("prod-1", 1, new BigDecimal("100.00"))
            ));

            var action = new OrderPercentageDiscountAction(new BigDecimal("0.20")); // 20%
            boolean applied = action.apply(workspace);

            Assertions.assertThat(applied).isTrue();

            WorkspaceLineItem item = workspace.getLineItems().getFirst();
            Assertions.assertThat(item.getDiscountAmount()).isEqualByComparingTo("20.00");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("percentageDiscountScenarios")
        @DisplayName("Calculates percentage discount for each line item individually")
        void apply_ShouldApplyPercentageDiscountToAllItems_WhenMultipleLineItemsPresent(
                String scenarioName,
                BigDecimal percentage,
                List<ItemSpec> itemSpecs,
                List<BigDecimal> expectedDiscounts
        ) {
            EngineWorkspace workspace = buildTestWorkspace(itemSpecs);

            var action = new OrderPercentageDiscountAction(percentage);
            boolean applied = action.apply(workspace);

            assertThat(applied).isTrue();

            List<WorkspaceLineItem> items = workspace.getLineItems();
            Assertions.assertThat(items).hasSameSizeAs(expectedDiscounts);

            Assertions.assertThat(items)
                    .extracting(WorkspaceLineItem::getDiscountAmount)
                    .usingElementComparator(BigDecimal::compareTo)
                    .containsExactlyElementsOf(expectedDiscounts);
        }

        private static Stream<Arguments> percentageDiscountScenarios() {
            /*
            The Stream's structure:
            1) Scenario name
            2) Discount percentage (e.g. 0.10 for 10%)
            3) Cart line items (productExternalId, quantity, unitPrice)
            4) Expected discount amount applied to each item
            */
            return Stream.of(
                    // 10% discount on $100.00 and $50.00 items -> $10.00 and $5.00
                    Arguments.of(
                            "Standard 10% discount across multiple items",
                            new BigDecimal("0.10"),
                            List.of(
                                    new ItemSpec("prod-1", 1, new BigDecimal("100.00")),
                                    new ItemSpec("prod-2", 1, new BigDecimal("50.00"))
                            ),
                            List.of(new BigDecimal("10.00"), new BigDecimal("5.00"))
                    ),
                    // 15% discount check with HALF_UP rounding ($99.99 * 0.15 = 14.9985 -> $15.00)
                    Arguments.of(
                            "15% discount with HALF_UP rounding",
                            new BigDecimal("0.15"),
                            List.of(
                                    new ItemSpec("prod-1", 1, new BigDecimal("99.99")),
                                    new ItemSpec("prod-2", 2, new BigDecimal("10.00")) // net subtotal: $20.00 -> $3.00 discount
                            ),
                            List.of(new BigDecimal("15.00"), new BigDecimal("3.00"))
                    ),
                    // 100% discount (1.00) -> Full item price as discount
                    Arguments.of(
                            "Full 100% discount",
                            new BigDecimal("1.00"),
                            List.of(
                                    new ItemSpec("prod-1", 1, new BigDecimal("45.50")),
                                    new ItemSpec("prod-2", 1, new BigDecimal("12.25"))
                            ),
                            List.of(new BigDecimal("45.50"), new BigDecimal("12.25"))
                    )
            );
        }
    }
}
