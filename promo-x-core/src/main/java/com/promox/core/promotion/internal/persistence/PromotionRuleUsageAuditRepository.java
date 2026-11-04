package com.promox.core.promotion.internal.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;


public interface PromotionRuleUsageAuditRepository extends JpaRepository<PromotionRuleUsageAudit, UUID> {
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
