package com.promox.core.promotion.internal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;


public interface PromotionRuleUsageAuditRepository extends JpaRepository<PromotionRuleUsageAudit, UUID> {
    /**
     * The storefront's audits, optionally only those that matched any of {@code :matchedRuleIds} and applied any of
     * {@code :appliedRuleIds}. An empty array doesn't filter.
     */
    String AUDIT_FILTER = """
        WHERE ua.storefront_id = :storefrontId
          AND (cardinality(CAST(:matchedRuleIds AS bigint[])) = 0 OR EXISTS (
                SELECT 1
                FROM promotion_rule_usage_audit_matched_rule mr
                WHERE mr.usage_id = ua.usage_id
                  AND mr.rule_id = ANY(CAST(:matchedRuleIds AS bigint[]))
              ))
          AND (cardinality(CAST(:appliedRuleIds AS bigint[])) = 0 OR EXISTS (
                SELECT 1
                FROM promotion_rule_usage_audit_applied_rule ar
                WHERE ar.usage_id = ua.usage_id
                  AND ar.rule_id = ANY(CAST(:appliedRuleIds AS bigint[]))
              ))
        """;

    /** The columns of {@link PromotionRuleUsageAuditRow}, in its order. */
    String AUDIT_SELECT = """
        SELECT
            ua.usage_id,
            u.status,
            ua.session_id,
            ua.customer_id,
            ua.promo_codes,
            ua.subtotal_before,
            ua.subtotal_after,
            ua.shipping_cost_before,
            ua.shipping_cost_after,
            ua.total_before,
            ua.total_after,
            ua.created_at
        FROM promotion_rule_usage_audit ua
        JOIN promotion_rule_usage u ON u.id = ua.usage_id
        """;

    /**
     * A page of the filtered audits, newest first. Two queries rather than a sort parameter, so each order can use
     * the {@code (storefront_id, created_at)} index.
     */
    @NativeQuery(AUDIT_SELECT + AUDIT_FILTER + """
        ORDER BY ua.created_at DESC, ua.usage_id DESC
        LIMIT :limit OFFSET :offset
        """)
    List<PromotionRuleUsageAuditRow> findAuditPageNewestFirst(
        @Param("storefrontId") Long storefrontId,
        @Param("matchedRuleIds") Long[] matchedRuleIds,
        @Param("appliedRuleIds") Long[] appliedRuleIds,
        @Param("limit") int limit,
        @Param("offset") long offset
    );

    /** A page of the filtered audits, oldest first. */
    @NativeQuery(AUDIT_SELECT + AUDIT_FILTER + """
        ORDER BY ua.created_at ASC, ua.usage_id ASC
        LIMIT :limit OFFSET :offset
        """)
    List<PromotionRuleUsageAuditRow> findAuditPageOldestFirst(
        @Param("storefrontId") Long storefrontId,
        @Param("matchedRuleIds") Long[] matchedRuleIds,
        @Param("appliedRuleIds") Long[] appliedRuleIds,
        @Param("limit") int limit,
        @Param("offset") long offset
    );

    /** How many audits the filter keeps. The status isn't needed, so the usage isn't joined. */
    @NativeQuery("SELECT COUNT(*) FROM promotion_rule_usage_audit ua\n" + AUDIT_FILTER)
    long countAudits(
        @Param("storefrontId") Long storefrontId,
        @Param("matchedRuleIds") Long[] matchedRuleIds,
        @Param("appliedRuleIds") Long[] appliedRuleIds
    );

    /** The rules whose conditions the usages matched, each usage's from the highest priority down. */
    @NativeQuery("""
        SELECT mr.usage_id, r.id, r.name
        FROM promotion_rule_usage_audit_matched_rule mr
        JOIN promotion_rule r ON r.id = mr.rule_id
        WHERE mr.usage_id IN (:usageIds)
        ORDER BY mr.usage_id, r.priority DESC, r.id
        """)
    List<PromotionRuleUsageAuditRuleRow> findMatchedRules(@Param("usageIds") Collection<UUID> usageIds);

    /** The rules the usages applied, each usage's in the order they were applied. */
    @NativeQuery("""
        SELECT ar.usage_id, r.id, r.name
        FROM promotion_rule_usage_audit_applied_rule ar
        JOIN promotion_rule r ON r.id = ar.rule_id
        JOIN promotion_rule_usage u ON u.id = ar.usage_id
        WHERE ar.usage_id IN (:usageIds)
        ORDER BY ar.usage_id, array_position(u.applied_rule_ids, r.id)
        """)
    List<PromotionRuleUsageAuditRuleRow> findAppliedRules(@Param("usageIds") Collection<UUID> usageIds);

    /**
     * Aggregates the storefront's confirmed ({@code USED}) usages evaluated within {@code [from, to)}.
     * Averages are null when there is no usage in the range.
     */
    @NativeQuery("""
        SELECT
            COUNT(*)                                           AS usage_count,
            COALESCE(SUM(cardinality(ua.promo_codes)), 0)      AS entered_promo_code_count,
            COUNT(*) FILTER (WHERE ua.customer_id IS NOT NULL) AS registered_customer_usage_count,
            COUNT(*) FILTER (WHERE ua.customer_id IS NULL)     AS anonymous_customer_usage_count,
            ROUND(AVG(ua.total_before - ua.total_after), 4)                 AS average_total_discount,
            ROUND(AVG(ua.subtotal_before - ua.subtotal_after), 4)           AS average_subtotal_discount,
            ROUND(AVG(ua.shipping_cost_before - ua.shipping_cost_after), 4) AS average_shipping_discount
        FROM promotion_rule_usage_audit ua
        JOIN promotion_rule_usage u ON u.id = ua.usage_id
        WHERE ua.storefront_id = :storefrontId
          AND u.status = 'USED'
          AND ua.created_at >= :from
          AND ua.created_at < :to
        """)
    PromotionRuleUsageStats aggregateUsageStats(
        @Param("storefrontId") Long storefrontId,
        @Param("from") Instant from,
        @Param("to") Instant to
    );

    /**
     * Counts the storefront's confirmed ({@code USED}) usages per UTC day within {@code [from, to)},
     * oldest day first. Every day the range touches is present, with 0 when it has no usage.
     * {@code to} is exclusive, so a range ending at midnight doesn't include that day.
     */
    @NativeQuery("""
        WITH daily_usage AS (
            SELECT
                CAST(ua.created_at AT TIME ZONE 'UTC' AS date) AS date,
                COUNT(*)                                        AS usage_count
            FROM promotion_rule_usage_audit ua
            JOIN promotion_rule_usage u ON u.id = ua.usage_id
            WHERE ua.storefront_id = :storefrontId
              AND u.status = 'USED'
              AND ua.created_at >= :from
              AND ua.created_at < :to
            GROUP BY 1
        )
        SELECT
            CAST(day AS date)                 AS date,
            COALESCE(du.usage_count, 0)       AS usage_count
        FROM generate_series(
            CAST(CAST(:from AS timestamptz) AT TIME ZONE 'UTC' AS date),
            CAST((CAST(:to AS timestamptz) - INTERVAL '1 microsecond') AT TIME ZONE 'UTC' AS date),
            INTERVAL '1 day'
        ) AS day
        LEFT JOIN daily_usage du ON du.date = CAST(day AS date)
        ORDER BY day
        """)
    List<PromotionRuleUsageDailyCount> countUsagesPerDay(
        @Param("storefrontId") Long storefrontId,
        @Param("from") Instant from,
        @Param("to") Instant to
    );
}
