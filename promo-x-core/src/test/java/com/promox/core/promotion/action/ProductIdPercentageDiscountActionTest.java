package com.promox.core.promotion.action;

import com.promox.core.promotion.dto.LineItemRequest;
import com.promox.core.promotion.dto.PromotionEvaluationRequest;
import com.promox.core.promotion.internal.action.ProductIdPercentageDiscountAction;
import com.promox.core.promotion.internal.context.EngineWorkspace;
import com.promox.core.promotion.internal.context.WorkspaceLineItem;
import com.promox.core.promotion.internal.exception.InvalidActionStrategyException;

import static com.promox.core.promotion.internal.exception.ActionStrategyErrorCodes.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;


public class ProductIdPercentageDiscountActionTest {
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

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("Constructor throws MISSING_REQUIRED_FIELD when product ID is null or blank")
    void constructor_ShouldThrowException_WhenProductIdIsNullMatchesOrBlank(String invalidProductId) {
        assertThatThrownBy(() -> new ProductIdPercentageDiscountAction(invalidProductId, new BigDecimal("0.20")))
                .isInstanceOf(InvalidActionStrategyException.class)
                .extracting("errorCode")
                .isEqualTo(MISSING_REQUIRED_FIELD);
    }

    @Test
    @DisplayName("Constructor throws MISSING_REQUIRED_FIELD when discount percentage is null")
    void constructor_ShouldThrowException_WhenPercentageIsNull() {
        assertThatThrownBy(() -> new ProductIdPercentageDiscountAction("PROD-123", null))
                .isInstanceOf(InvalidActionStrategyException.class)
                .extracting("errorCode")
                .isEqualTo(MISSING_REQUIRED_FIELD);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-50.00", "-0.10", "0.00", "1.01", "1.50", "10.00"})
    void constructor_ShouldThrowException_WhenPercentageIsOutOfRange(String invalidPercentage) {
        var percentage = new BigDecimal(invalidPercentage);

        assertThatThrownBy(() -> new ProductIdPercentageDiscountAction("PROD-123", percentage))
                .isInstanceOf(InvalidActionStrategyException.class)
                .extracting("errorCode")
                .isEqualTo(INVALID_VALUE_RANGE);
    }

    @Test
    @DisplayName("Returns false when no line items match the target product ID")
    void apply_ShouldReturnFalse_WhenNoMatchingProductIdInWorkspace() {
        EngineWorkspace workspace = buildTestWorkspace(List.of(
                new ItemSpec("PROD-999", 1, new BigDecimal("10.00"))
        ));

        var action = new ProductIdPercentageDiscountAction("PROD-123", new BigDecimal("0.20"));

        boolean result = action.apply(workspace);

        assertThat(result).isFalse();
        assertThat(workspace.getLineItems().getFirst().getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Returns false when matching item has zero price")
    void apply_ShouldReturnFalse_WhenMatchingItemHasZeroNetPrice() {
        EngineWorkspace workspace = buildTestWorkspace(List.of(
                new ItemSpec("PROD-123", 1, BigDecimal.ZERO)
        ));

        var action = new ProductIdPercentageDiscountAction("PROD-123", new BigDecimal("0.50"));

        boolean result = action.apply(workspace);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Returns false when product ID case does not match exactly")
    void apply_ShouldReturnFalse_WhenProductIdCaseDoesNotMatch() {
        EngineWorkspace workspace = buildTestWorkspace(List.of(
                new ItemSpec("prod-123", 1, new BigDecimal("50.00"))
        ));

        var action = new ProductIdPercentageDiscountAction("PROD-123", new BigDecimal("0.10"));

        boolean result = action.apply(workspace);

        assertThat(result).isFalse();
        assertThat(workspace.getLineItems().getFirst().getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Only applies discount to matching product items, ignoring non-matching items")
    void apply_ShouldDiscountOnlyMatchingItems_WhenCartHasMultipleProducts() {
        EngineWorkspace workspace = buildTestWorkspace(List.of(
                new ItemSpec("PROD-100", 1, new BigDecimal("40.00")),
                new ItemSpec("PROD-200", 1, new BigDecimal("20.00"))
        ));

        var action = new ProductIdPercentageDiscountAction("PROD-100", new BigDecimal("0.25"));

        boolean result = action.apply(workspace);

        assertThat(result).isTrue();

        WorkspaceLineItem targetItem = workspace.getLineItems().get(0);
        WorkspaceLineItem otherItem = workspace.getLineItems().get(1);

        assertThat(targetItem.getDiscountAmount()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(otherItem.getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "20% discount on $100.00, 0.20, 100.00, 20.00",
            "100% full discount on $15.50, 1.00, 15.50, 15.50",
            "15% discount with HALF_UP rounding ($10.33 * 0.15 = 1.5495 -> 1.55), 0.15, 10.33, 1.55"
    })
    void apply_ShouldCalculateDiscountAndRoundingCorrectly(
            String scenario,
            String percentage,
            String unitPrice,
            String expectedDiscount
    ) {
        EngineWorkspace workspace = buildTestWorkspace(List.of(
                new ItemSpec("PROD-1", 1, new BigDecimal(unitPrice))
        ));

        var action = new ProductIdPercentageDiscountAction("PROD-1", new BigDecimal(percentage));

        boolean result = action.apply(workspace);

        assertThat(result).isTrue();
        assertThat(workspace.getLineItems().getFirst().getDiscountAmount())
                .isEqualByComparingTo(new BigDecimal(expectedDiscount));
    }
}
