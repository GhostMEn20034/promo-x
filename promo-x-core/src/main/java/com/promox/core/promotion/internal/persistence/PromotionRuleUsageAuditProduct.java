package com.promox.core.promotion.internal.persistence;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;


@Entity
@Table(name = "promotion_rule_usage_audit_product")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PromotionRuleUsageAuditProduct {
    // Own key: an audit has one row per order line, so the audit ID can't be the primary key.
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "rule_usage_audit_id", nullable = false, updatable = false)
    private UUID ruleUsageAuditId;

    @Column(name = "storefront_id", nullable = false, updatable = false)
    private Long storefrontId;

    @Column(name = "product_external_id", nullable = false, updatable = false)
    private String productExternalId;

    @Column(name = "product_sku", nullable = false, updatable = false)
    private String productSku;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false)
    private List<String> categories; // collection of the categories external ids

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;
}
