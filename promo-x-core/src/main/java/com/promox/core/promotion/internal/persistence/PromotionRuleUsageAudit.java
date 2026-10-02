package com.promox.core.promotion.internal.persistence;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;


@Entity
@Table(name = "promotion_rule_usage_audit")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PromotionRuleUsageAudit {
    // Derived from `usage` through @MapsId; leave it unset when creating an audit.
    @Id
    @Column(name = "usage_id", nullable = false, updatable = false)
    private UUID usageId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "usage_id")
    private PromotionRuleUsage usage;

    @Column(name = "storefront_id", nullable = false)
    private Long storefrontId;

    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "customer_id")
    private String customerId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "promo_codes", nullable = false)
    private Set<String> promoCodes;

    @Column(name = "shipping_cost_before", nullable = false, precision = 19, scale = 4)
    private BigDecimal shippingCostBefore;

    @Column(name = "shipping_cost_after", nullable = false, precision = 19, scale = 4)
    private BigDecimal shippingCostAfter;

    @Column(name = "subtotal_before", nullable = false, precision = 19, scale = 4)
    private BigDecimal subtotalBefore;

    @Column(name = "subtotal_after", nullable = false, precision = 19, scale = 4)
    private BigDecimal subtotalAfter;

    @Column(name = "total_before", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalBefore;

    @Column(name = "total_after", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAfter;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "promotion_rule_usage_audit_applied_rule",
        joinColumns = @JoinColumn(name = "usage_id"),
        inverseJoinColumns = @JoinColumn(name = "rule_id")
    )
    @Builder.Default
    private Set<PromotionRule> appliedRules = new HashSet<>();

    /**
     * Rules whose conditions matched, whether or not their action ended up being applied.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "promotion_rule_usage_audit_matched_rule",
        joinColumns = @JoinColumn(name = "usage_id"),
        inverseJoinColumns = @JoinColumn(name = "rule_id")
    )
    @Builder.Default
    private Set<PromotionRule> matchedRules = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
