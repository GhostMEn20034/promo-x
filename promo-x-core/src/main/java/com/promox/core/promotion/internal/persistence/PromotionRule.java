package com.promox.core.promotion.internal.persistence;

import com.promox.core.promotion.internal.service.action.ActionStrategy;
import com.promox.core.promotion.internal.service.condition.ConditionNode;

import jakarta.persistence.*;
import lombok.*;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;


@Entity
@Table(name = "promotion_rule")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PromotionRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "storefront_id", nullable = false)
    private Long storefrontId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int priority;

    // Engine Short-Circuit Strategy Anchor: If true, no lower-priority rules can run.
    @Column(name = "is_short_circuit", nullable = false)
    private boolean isShortCircuit = false;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    /**
     * The logical condition tree that must evaluate to true for the promotion action to be executed.
     * */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "condition_tree", columnDefinition = "jsonb", nullable = false)
    private ConditionNode conditionTree;

    /**
     * The promotion action (e.g., order discount, item discount, free shipping) applied to the user's order/cart.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private ActionStrategy action;

    @Column(name = "has_usage_limit", nullable = false)
    @Getter(AccessLevel.NONE)
    private boolean hasUsageLimit = false;

    /**
     * Total usage budget configured for this rule (informative baseline / max cap).
     * Nullable when usage tracking is disabled.
     */
    @Column(name = "usage_limit", nullable = false)
    private Integer usageLimit = 0;

    /**
     * Current available usages remaining. Decremented when the rule triggers.
     * Nullable when usage tracking is disabled.
     */
    @Column(name = "remaining_uses", nullable = false)
    private Integer remainingUses = 0;

    @ElementCollection
    @CollectionTable(
        name = "rule_product",
        joinColumns = @JoinColumn(name = "rule_id")
    )
    @Column(name = "product_external_id", nullable = false)
    private Set<String> productExternalIds = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean hasUsageLimit() {
        return this.hasUsageLimit;
    }
}