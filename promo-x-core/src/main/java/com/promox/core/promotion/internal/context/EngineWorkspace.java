package com.promox.core.promotion.internal.context;

import com.promox.core.promotion.dto.PromotionEvaluationRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


public class EngineWorkspace {
    private final String storefrontId;
    private final String sessionId;
    private final String customerId;
    private final boolean isCustomerAnonymous;
    private final List<String> promoCodes;

    // Core Engine runtime containers
    private final List<WorkspaceLineItem> lineItems;
    private final List<AppliedRuleLog> appliedRules;

    private final BigDecimal shippingCost;
    private BigDecimal shippingCostDiscount;

    public EngineWorkspace(PromotionEvaluationRequest request) {
        this.storefrontId = request.storefrontId();
        this.sessionId = request.sessionId();
        this.customerId = request.customerId();
        this.promoCodes = request.promoCodes();
        this.isCustomerAnonymous = request.customerId() == null;
        this.shippingCost = request.shippingCost();

        this.appliedRules = new ArrayList<>();
        this.lineItems = request.items().stream()
                .map(WorkspaceLineItem::new)
                .collect(Collectors.toList());
    }

    public void logAppliedRule(String ruleId, String ruleName) {
        this.appliedRules.add(new AppliedRuleLog(ruleId, ruleName));
    }

    public void addShippingDiscount(BigDecimal amount) {
        this.shippingCostDiscount = this.shippingCostDiscount.add(amount);
    }

    /**
     * Resets the entire internal state back to original incoming baseline parameters.
     * Essential for handling GLOBAL exclusivity overrides safely.
     */
    public void resetToRawBaselines() {
        this.appliedRules.clear();
        this.shippingCostDiscount = BigDecimal.ZERO;
        for (WorkspaceLineItem item : this.lineItems) {
            item.setDiscountAmount(BigDecimal.ZERO);
        }
    }

    public BigDecimal getItemsSubtotal() {
        return lineItems.stream()
                .map(WorkspaceLineItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getItemsDiscount() {
        return lineItems.stream()
                .map(WorkspaceLineItem::getDiscountAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getNewItemsSubtotal() {
        return lineItems.stream()
                .map(WorkspaceLineItem::getNewTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getNewShippingCost() {
        BigDecimal cost = this.shippingCost.subtract(this.shippingCostDiscount);
        return cost.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : cost;
    }

    public BigDecimal getTotalDiscount() {
        return getItemsDiscount().add(this.shippingCostDiscount);
    }

    public BigDecimal getGrandTotal() {
        return getNewItemsSubtotal().add(getNewShippingCost());
    }

    public String getStorefrontId() { return storefrontId; }
    public String getSessionId() { return sessionId; }
    public String getCustomerId() { return customerId; }
    public boolean isCustomerAnonymous() { return isCustomerAnonymous; }
    public List<String> getPromoCodes() { return promoCodes; }
    public List<WorkspaceLineItem> getLineItems() { return lineItems; }
    public List<AppliedRuleLog> getAppliedRules() { return appliedRules; }
    public BigDecimal getShippingCost() { return shippingCost; }
    public BigDecimal getShippingCostDiscount() { return shippingCostDiscount; }
}