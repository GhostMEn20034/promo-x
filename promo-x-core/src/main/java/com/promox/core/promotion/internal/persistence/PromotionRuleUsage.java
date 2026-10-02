package com.promox.core.promotion.internal.persistence;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;


@Entity
@Table(name = "promotion_rule_usage")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PromotionRuleUsage {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "storefront_id", nullable = false, updatable = false)
    private Long storefrontId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PromotionRuleUsageStatus status;

    /**
     * IDs of every rule applied in this usage, in application order. Kept as an ID array
     * rather than a relation so reading a usage never joins the rules.
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "applied_rule_ids", nullable = false, updatable = false)
    private List<Long> appliedRuleIds;

    /**
     * Subset of {@link #appliedRuleIds} that had a usage limit when applied, i.e. the rules whose
     * quota this usage holds. Canceling gives back exactly these, whatever the rules look like by then.
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "limited_rule_ids", nullable = false, updatable = false)
    private List<Long> limitedRuleIds;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * When a {@link PromotionRuleUsageStatus#RESERVED} usage is due to expire if it isn't confirmed.
     * Set on creation from the configured reservation TTL; null when the usage was never reserved.
     */
    @Column(name = "expires_at")
    private Instant expiresAt;

    /**
     * When the usage was actually moved to {@link PromotionRuleUsageStatus#EXPIRED}; null until then.
     * Not to be confused with {@link #expiresAt}, which is the planned deadline.
     */
    @Column(name = "expired_at")
    private Instant expiredAt;
}
