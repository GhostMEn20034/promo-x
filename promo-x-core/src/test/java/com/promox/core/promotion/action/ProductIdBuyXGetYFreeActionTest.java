package com.promox.core.promotion.action;

import com.promox.core.promotion.dto.LineItemRequest;
import com.promox.core.promotion.dto.PromotionEvaluationRequest;
import com.promox.core.promotion.internal.action.ProductIdBuyXGetYFreeAction;
import com.promox.core.promotion.internal.context.EngineWorkspace;
import com.promox.core.promotion.internal.context.WorkspaceLineItem;
import com.promox.core.promotion.internal.exception.InvalidActionStrategyException;

import static com.promox.core.promotion.internal.exception.ActionStrategyErrorCodes.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;


public class ProductIdBuyXGetYFreeActionTest {
    private record ItemSpec(String productId, int quantity, BigDecimal unitPrice) {}

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
                "us-storefront",
                "sess-1",
                "cust-1",
                List.of(),
                items,
                BigDecimal.ZERO
        ));
    }

    @Nested
    class ConstructorValidationTests {
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("Throws MISSING_REQUIRED_FIELD when target product ID is null or blank")
        void constructor_ShouldThrowException_WhenProductIdIsNullMatchesOrBlank(String invalidProductId) {
            assertThatThrownBy(() -> new ProductIdBuyXGetYFreeAction(invalidProductId, 2, 1))
                    .isInstanceOf(InvalidActionStrategyException.class)
                    .extracting("errorCode")
                    .isEqualTo(MISSING_REQUIRED_FIELD);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(ints = {0, -1, -5})
        @DisplayName("Throws INVALID_VALUE_RANGE when buyQuantity is null, zero, or negative")
        void constructor_ShouldThrowException_WhenBuyQuantityIsInvalid(Integer invalidBuyQuantity) {
            assertThatThrownBy(() -> new ProductIdBuyXGetYFreeAction("PROD-123", invalidBuyQuantity, 1))
                    .isInstanceOf(InvalidActionStrategyException.class)
                    .extracting("errorCode")
                    .isEqualTo(INVALID_VALUE_RANGE);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(ints = {0, -1, -5})
        @DisplayName("Throws INVALID_VALUE_RANGE when freeQuantity is null, zero, or negative")
        void constructor_ShouldThrowException_WhenFreeQuantityIsInvalid(Integer invalidFreeQuantity) {
            assertThatThrownBy(() -> new ProductIdBuyXGetYFreeAction("PROD-123", 2, invalidFreeQuantity))
                    .isInstanceOf(InvalidActionStrategyException.class)
                    .extracting("errorCode")
                    .isEqualTo(INVALID_VALUE_RANGE);
        }
    }

    @Test
    @DisplayName("Returns false when line items array is completely empty")
    void apply_ShouldReturnFalse_WhenWorkspaceHasNoLineItems() {
        EngineWorkspace workspace = buildTestWorkspace(List.of());

        var action = new ProductIdBuyXGetYFreeAction("PROD-123", 2, 1);

        boolean result = action.apply(workspace);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Returns false when target product ID is absent from workspace line items")
    void apply_ShouldReturnFalse_WhenTargetProductIdIsNotInWorkspace() {
        EngineWorkspace workspace = buildTestWorkspace(List.of(
                new ItemSpec("PROD-999", 3, new BigDecimal("10.00"))
        ));

        var action = new ProductIdBuyXGetYFreeAction("PROD-123", 2, 1);

        boolean result = action.apply(workspace);

        assertThat(result).isFalse();
        assertThat(workspace.getLineItems().getFirst().getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Returns false when item quantity is lower than group size (buy + free)")
    void apply_ShouldReturnFalse_WhenQuantityIsInsufficientForPromotion() {
        EngineWorkspace workspace = buildTestWorkspace(List.of(
                new ItemSpec("PROD-123", 2, new BigDecimal("15.00"))
        ));

        var action = new ProductIdBuyXGetYFreeAction("PROD-123", 2, 1);

        boolean result = action.apply(workspace);

        assertThat(result).isFalse();
        assertThat(workspace.getLineItems().getFirst().getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Only applies discount to target product when workspace contains multiple items")
    void apply_ShouldOnlyDiscountTargetProduct_WhenWorkspaceContainsMultipleProducts() {
        // Arrange: Buy 2 Get 1 Free on PROD-100
        EngineWorkspace workspace = buildTestWorkspace(List.of(
                new ItemSpec("PROD-100", 3, new BigDecimal("20.00")), // Target item: 1 free ($20.00)
                new ItemSpec("PROD-200", 2, new BigDecimal("50.00")), // Non-target item
                new ItemSpec("PROD-300", 1, new BigDecimal("100.00")) // Non-target item
        ));

        var action = new ProductIdBuyXGetYFreeAction("PROD-100", 2, 1);

        boolean result = action.apply(workspace);

        assertThat(result).isTrue();

        WorkspaceLineItem targetItem = workspace.getLineItems().getFirst();
        WorkspaceLineItem otherItem1 = workspace.getLineItems().get(1);
        WorkspaceLineItem otherItem2 = workspace.getLineItems().get(2);

        assertThat(targetItem.getDiscountAmount()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(otherItem1.getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(otherItem2.getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Returns false when workspace has multiple items but target product quantity is below threshold")
    void apply_ShouldReturnFalse_WhenWorkspaceHasMultipleProductsButTargetDoesNotQualify() {
        // Arrange: Target PROD-100 only has 2 units, but needs 3 (Buy 2 Get 1)
        EngineWorkspace workspace = buildTestWorkspace(List.of(
                new ItemSpec("PROD-100", 2, new BigDecimal("20.00")),
                new ItemSpec("PROD-200", 5, new BigDecimal("10.00"))
        ));

        var action = new ProductIdBuyXGetYFreeAction("PROD-100", 2, 1);

        // Act
        boolean result = action.apply(workspace);

        // Assert
        assertThat(result).isFalse();
        assertThat(workspace.getLineItems().get(0).getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(workspace.getLineItems().get(1).getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Returns false when matching line item is already fully discounted (net price <= 0)")
    void apply_ShouldReturnFalse_WhenItemHasZeroNetPrice() {
        EngineWorkspace workspace = buildTestWorkspace(List.of(
                new ItemSpec("PROD-123", 3, BigDecimal.ZERO)
        ));

        var action = new ProductIdBuyXGetYFreeAction("PROD-123", 2, 1);

        boolean result = action.apply(workspace);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Applies discount correctly for Buy 2 Get 1 Free with 3 items in cart")
    void apply_ShouldDeductOneUnitPrice_WhenBuy2Get1FreeTriggeredWith3Items() {
        EngineWorkspace workspace = buildTestWorkspace(List.of(
                new ItemSpec("PROD-100", 3, new BigDecimal("10.00"))
        ));

        var action = new ProductIdBuyXGetYFreeAction("PROD-100", 2, 1);

        boolean result = action.apply(workspace);

        assertThat(result).isTrue();

        WorkspaceLineItem item = workspace.getLineItems().get(0);
        assertThat(item.getDiscountAmount()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(item.getNetPrice()).isEqualByComparingTo(new BigDecimal("20.00"));
    }

    @ParameterizedTest(name = "Buy {1} Get {2} Free with cart quantity {3} @ ${4} -> expect discount ${5}")
    @CsvSource({
            "Buy 2 Get 1 with 3 items (1 free), 2, 1, 3, 25.00, 25.00",
            "Buy 2 Get 1 with 6 items (2 free), 2, 1, 6, 25.00, 50.00",
            "'Buy 2 Get 1 with 7 items (2 free, 1 remainder)', 2, 1, 7, 25.00, 50.00",
            "Buy 1 Get 1 with 4 items (2 free), 1, 1, 4, 12.50, 25.00",
            "Buy 3 Get 2 with 10 items (4 free across 2 groups), 3, 2, 10, 10.00, 40.00"
    })
    void apply_ShouldCalculateProportionalFreeUnitsAndDiscount(
            String scenario,
            int buyQty,
            int freeQty,
            int totalCartQty,
            String unitPrice,
            String expectedDiscount
    ) {
        EngineWorkspace workspace = buildTestWorkspace(List.of(
                new ItemSpec("PROD-1", totalCartQty, new BigDecimal(unitPrice))
        ));

        var action = new ProductIdBuyXGetYFreeAction("PROD-1", buyQty, freeQty);

        boolean result = action.apply(workspace);

        assertThat(result).isTrue();
        assertThat(workspace.getLineItems().getFirst().getDiscountAmount())
                .isEqualByComparingTo(new BigDecimal(expectedDiscount));
    }

    @Test
    @DisplayName("Applies discount when Product ID matches case-insensitively")
    void apply_ShouldDiscountItem_WhenProductIdMatchesCaseInsensitively() {
        EngineWorkspace workspace = buildTestWorkspace(List.of(
                new ItemSpec("prod-abc", 3, new BigDecimal("15.00"))
        ));

        var action = new ProductIdBuyXGetYFreeAction("PROD-ABC", 2, 1);

        boolean result = action.apply(workspace);

        assertThat(result).isTrue();
        assertThat(workspace.getLineItems().getFirst().getDiscountAmount())
                .isEqualByComparingTo(new BigDecimal("15.00"));
    }
}
