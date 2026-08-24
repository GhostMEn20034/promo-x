package com.promox.core.promotion.internal.context;


import com.promox.core.promotion.internal.dto.LineItemRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;


public class WorkspaceLineItem {
    private final String productExternalId;
    private final String productSku;
    private List<String> categories;
    private List<String> collections;
    private final int quantity;
    private final BigDecimal unitPrice;

    private BigDecimal discountAmount;

    public WorkspaceLineItem(LineItemRequest request) {
        this.productExternalId = request.productExternalId();
        this.productSku = request.productSku();
        this.categories = request.categories() != null ? new ArrayList<>(request.categories()) : new ArrayList<>();
        this.collections = request.collections() != null ? new ArrayList<>(request.collections()) : new ArrayList<>();
        this.quantity = request.quantity();
        this.unitPrice = request.unitPrice();

        this.discountAmount = BigDecimal.ZERO;
    }

    public BigDecimal getTotalPrice() {
        return this.unitPrice.multiply(BigDecimal.valueOf(this.quantity));
    }

    public BigDecimal getNetPrice() {
        BigDecimal net = getTotalPrice().subtract(this.discountAmount);
        return net.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : net;
    }

    public boolean addDiscount(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        BigDecimal remainingPrice = getNetPrice();
        if (remainingPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return false; // Item is already fully free
        }

        // Cap the discount so we don't over-discount the line item
        BigDecimal actualDiscount = amount.min(remainingPrice);
        this.discountAmount = this.discountAmount.add(actualDiscount);
        return true;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public String getProductExternalId() { return productExternalId; }
    public String getProductSku() { return productSku; }
    public List<String> getCategories() { return categories; }
    public List<String> getCollections() { return collections; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getDiscountAmount() { return discountAmount; }

    public void setCollections(List<String> collections) { this.collections = collections; }
    public void setCategories(List<String> categories) { this.categories = categories; }
}
