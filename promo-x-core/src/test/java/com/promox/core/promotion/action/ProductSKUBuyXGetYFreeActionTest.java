package com.promox.core.promotion.action;

import com.promox.core.promotion.internal.dto.LineItemRequest;
import com.promox.core.promotion.internal.dto.PromotionEvaluationRequest;
import com.promox.core.promotion.internal.service.action.ProductSKUBuyXGetYFreeAction;
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



public class ProductSKUBuyXGetYFreeActionTest {
    private static final Long STOREFRONT_ID = 123L;

    private record ItemSpec(String productId, String sku, int quantity, BigDecimal unitPrice) {}

    private EngineWorkspace buildTestWorkspace(List<ItemSpec> specs) {
        List<LineItemRequest> items = new ArrayList<>();
        for (ItemSpec spec : specs) {
            items.add(new LineItemRequest(
                    spec.productId(),
                    spec.sku(),
                    List.of("Electronics"),
                    List.of("Default"),
                    spec.quantity(),
                    spec.unitPrice()
            ));
        }

        return new EngineWorkspace(STOREFRONT_ID, new PromotionEvaluationRequest(
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
        @DisplayName("Throws MISSING_REQUIRED_FIELD when target SKU is null or blank")
        void constructor_ShouldThrowException_WhenSkuIsNullMatchesOrBlank(String invalidSku) {
            assertThatThrownBy(() -> new ProductSKUBuyXGetYFreeAction(invalidSku, 2, 1))
                    .isInstanceOf(InvalidActionStrategyException.class)
                    .extracting("errorCode")
                    .isEqualTo(MISSING_REQUIRED_FIELD);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(ints = {0, -1, -5})
        @DisplayName("Throws INVALID_VALUE_RANGE when buyQuantity is null, zero, or negative")
        void constructor_ShouldThrowException_WhenBuyQuantityIsInvalid(Integer invalidBuyQuantity) {
            assertThatThrownBy(() -> new ProductSKUBuyXGetYFreeAction("SKU-123", invalidBuyQuantity, 1))
                    .isInstanceOf(InvalidActionStrategyException.class)
                    .extracting("errorCode")
                    .isEqualTo(INVALID_VALUE_RANGE);
        }

        @Test
        @DisplayName("Throws MISSING_REQUIRED_FIELD when freeQuantity is null")
        void constructor_ShouldThrowException_WhenFreeQuantityIsNull() {
            assertThatThrownBy(() -> new ProductSKUBuyXGetYFreeAction("SKU-123", 2, null))
                    .isInstanceOf(InvalidActionStrategyException.class)
                    .extracting("errorCode")
                    .isEqualTo(MISSING_REQUIRED_FIELD);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1, -5})
        @DisplayName("Throws INVALID_VALUE_RANGE when freeQuantity is zero or negative")
        void constructor_ShouldThrowException_WhenFreeQuantityIsZeroOrNegative(Integer invalidFreeQuantity) {
            assertThatThrownBy(() -> new ProductSKUBuyXGetYFreeAction("SKU-123", 2, invalidFreeQuantity))
                    .isInstanceOf(InvalidActionStrategyException.class)
                    .extracting("errorCode")
                    .isEqualTo(INVALID_VALUE_RANGE);
        }
    }

    @Test
    @DisplayName("Returns false when line items array is completely empty")
    void apply_ShouldReturnFalse_WhenWorkspaceHasNoLineItems() {
        EngineWorkspace workspace = buildTestWorkspace(List.of());

        var action = new ProductSKUBuyXGetYFreeAction("SKU-123", 2, 1);

        boolean result = action.apply(workspace);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Returns false when target SKU is absent from workspace line items")
    void apply_ShouldReturnFalse_WhenTargetSkuIsNotInWorkspace() {
        EngineWorkspace workspace = buildTestWorkspace(List.of(
                new ItemSpec("PROD-1", "SKU-999", 3, new BigDecimal("10.00"))
        ));

        var action = new ProductSKUBuyXGetYFreeAction("SKU-123", 2, 1);

        boolean result = action.apply(workspace);

        assertThat(result).isFalse();
        assertThat(workspace.getLineItems().getFirst().getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Returns false when total SKU quantity is lower than group size (buy + free)")
    void apply_ShouldReturnFalse_WhenQuantityIsInsufficientForPromotion() {
        EngineWorkspace workspace = buildTestWorkspace(List.of(
                new ItemSpec("PROD-1", "SKU-123", 2, new BigDecimal("15.00"))
        ));

        var action = new ProductSKUBuyXGetYFreeAction("SKU-123", 2, 1);

        boolean result = action.apply(workspace);

        assertThat(result).isFalse();
        assertThat(workspace.getLineItems().getFirst().getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Only applies discount to target SKU when workspace contains multiple distinct SKUs")
    void apply_ShouldOnlyDiscountTargetSku_WhenWorkspaceContainsMultipleSkus() {
        EngineWorkspace workspace = buildTestWorkspace(List.of(
                new ItemSpec("PROD-1", "SKU-100", 3, new BigDecimal("20.00")),
                new ItemSpec("PROD-2", "SKU-200", 2, new BigDecimal("50.00")),
                new ItemSpec("PROD-3", "SKU-300", 1, new BigDecimal("100.00"))
        ));

        var action = new ProductSKUBuyXGetYFreeAction("SKU-100", 2, 1);

        boolean result = action.apply(workspace);

        assertThat(result).isTrue();

        WorkspaceLineItem targetItem = workspace.getLineItems().get(0);
        WorkspaceLineItem otherItem1 = workspace.getLineItems().get(1);
        WorkspaceLineItem otherItem2 = workspace.getLineItems().get(2);

        assertThat(targetItem.getDiscountAmount()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(otherItem1.getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(otherItem2.getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Aggregates quantity across split line items sharing the same SKU and applies discount")
    void apply_ShouldAggregateQuantityAndDistributeFreeUnits_AcrossSplitLineItems() {
        // Arrange: Buy 2 Get 1 Free (group size = 3).
        // Split across 2 lines with same SKU: Line 1 (qty 2 @ $10), Line 2 (qty 1 @ $10)
        // Total SKU Qty = 3 -> 1 free unit ($10.00) allocated to Line 1
        EngineWorkspace workspace = buildTestWorkspace(List.of(
                new ItemSpec("PROD-1", "SKU-SPLIT", 2, new BigDecimal("10.00")),
                new ItemSpec("PROD-2", "SKU-SPLIT", 1, new BigDecimal("10.00"))
        ));

        var action = new ProductSKUBuyXGetYFreeAction("SKU-SPLIT", 2, 1);

        boolean result = action.apply(workspace);

        assertThat(result).isTrue();

        WorkspaceLineItem line1 = workspace.getLineItems().get(0);
        WorkspaceLineItem line2 = workspace.getLineItems().get(1);

        assertThat(line1.getDiscountAmount()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(line2.getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Spills over free unit distribution across multiple line items when first line capacity is exhausted")
    void apply_ShouldSpillOverFreeUnitsAcrossMultipleLines_WhenFirstLineQuantityIsExhausted() {
        // Arrange: Buy 1 Get 1 Free (group size = 2).
        // Line 1 (qty 1 @ $15), Line 2 (qty 3 @ $15)
        // Total Qty = 4 -> 2 free units ($30 total discount)
        // Line 1 absorbs 1 free unit ($15), Line 2 absorbs remaining 1 free unit ($15)
        EngineWorkspace workspace = buildTestWorkspace(List.of(
                new ItemSpec("PROD-1", "SKU-SPILL", 1, new BigDecimal("15.00")),
                new ItemSpec("PROD-2", "SKU-SPILL", 3, new BigDecimal("15.00"))
        ));

        var action = new ProductSKUBuyXGetYFreeAction("SKU-SPILL", 1, 1);

        boolean result = action.apply(workspace);

        assertThat(result).isTrue();

        WorkspaceLineItem line1 = workspace.getLineItems().get(0);
        WorkspaceLineItem line2 = workspace.getLineItems().get(1);

        assertThat(line1.getDiscountAmount()).isEqualByComparingTo(new BigDecimal("15.00"));
        assertThat(line2.getDiscountAmount()).isEqualByComparingTo(new BigDecimal("15.00"));
    }

    @Test
    @DisplayName("Returns false when matching line item is already fully discounted (net price <= 0)")
    void apply_ShouldReturnFalse_WhenItemHasZeroNetPrice() {
        EngineWorkspace workspace = buildTestWorkspace(List.of(
                new ItemSpec("PROD-1", "SKU-123", 3, BigDecimal.ZERO)
        ));

        var action = new ProductSKUBuyXGetYFreeAction("SKU-123", 2, 1);

        boolean result = action.apply(workspace);

        assertThat(result).isFalse();
    }

    @ParameterizedTest(name = "Buy {1} Get {2} Free with cart quantity {3} @ ${4} -> expect discount ${5}")
    @CsvSource({
            "'Buy 2 Get 1 with 3 items (1 free)', 2, 1, 3, 25.00, 25.00",
            "'Buy 2 Get 1 with 6 items (2 free)', 2, 1, 6, 25.00, 50.00",
            "'Buy 2 Get 1 with 7 items (2 free, 1 remainder)', 2, 1, 7, 25.00, 50.00",
            "'Buy 1 Get 1 with 4 items (2 free)', 1, 1, 4, 12.50, 25.00",
            "'Buy 3 Get 2 with 10 items (4 free across 2 groups)', 3, 2, 10, 10.00, 40.00"
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
                new ItemSpec("PROD-1", "SKU-1", totalCartQty, new BigDecimal(unitPrice))
        ));

        var action = new ProductSKUBuyXGetYFreeAction("SKU-1", buyQty, freeQty);

        boolean result = action.apply(workspace);

        assertThat(result).isTrue();
        assertThat(workspace.getLineItems().getFirst().getDiscountAmount())
                .isEqualByComparingTo(new BigDecimal(expectedDiscount));
    }

    @Test
    @DisplayName("Applies discount when SKU matches case-insensitively")
    void apply_ShouldDiscountItem_WhenSkuMatchesCaseInsensitively() {
        EngineWorkspace workspace = buildTestWorkspace(List.of(
                new ItemSpec("PROD-1", "sku-abc", 3, new BigDecimal("15.00"))
        ));

        var action = new ProductSKUBuyXGetYFreeAction("SKU-ABC", 2, 1);

        boolean result = action.apply(workspace);

        assertThat(result).isTrue();
        assertThat(workspace.getLineItems().getFirst().getDiscountAmount())
                .isEqualByComparingTo(new BigDecimal("15.00"));
    }
}
