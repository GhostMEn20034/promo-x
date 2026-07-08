package com.promox.core.promotion.internal.context;


import com.promox.core.promotion.dto.LineItemRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;


public class WorkspaceLineItem {
    private final String productId;
    private final String productSku;
    private List<String> categories;
    private List<String> collections;
    private final int quantity;
    private final BigDecimal unitPrice;

    private BigDecimal discountAmount;

    public WorkspaceLineItem(LineItemRequest request) {
        this.productId = request.productId();
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

    public BigDecimal getNewTotalPrice() {
        BigDecimal result = getTotalPrice().subtract(this.discountAmount);
        return result.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : result; // Guard against negative balances
    }

    public void addDiscount(BigDecimal amount) {
        this.discountAmount = this.discountAmount.add(amount);
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public String getProductId() { return productId; }
    public String getProductSku() { return productSku; }
    public List<String> getCategories() { return categories; }
    public List<String> getCollections() { return collections; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getDiscountAmount() { return discountAmount; }

    public void setCollections(List<String> collections) { this.collections = collections; }
    public void setCategories(List<String> categories) { this.categories = categories; }
}
