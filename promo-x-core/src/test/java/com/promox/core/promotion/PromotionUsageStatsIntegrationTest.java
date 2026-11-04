package com.promox.core.promotion;

import com.promox.core.infrastructure.security.Roles;
import com.promox.core.promotion.internal.dto.PromotionRuleUsageDailyCountDto;
import com.promox.core.promotion.internal.dto.PromotionRuleUsageStatsResponse;
import com.promox.core.promotion.internal.persistence.PromotionRuleUsageStatus;
import com.promox.core.promotion.internal.service.PromotionLookupApiImpl;
import com.promox.core.shared.IntegrationTestBase;
import com.promox.core.testfixtures.security.WithMockJwt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WithMockJwt(roles = Roles.PROMOTION_MANAGER)
public class PromotionUsageStatsIntegrationTest extends IntegrationTestBase {
    private static final String STATS_URL = "/promotion/usage/stats";
    private static final long STOREFRONT_ID = 100L;

    private static final Instant FROM = Instant.parse("2026-01-10T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-01-13T00:00:00Z");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    @DisplayName("Should sum counts and average the discounts of the usages inside the range")
    void getUsageStats_ShouldAggregateUsages_WhenTheyAreInsideRange() throws Exception {
        // total, subtotal and shipping discounts: (20, 15, 5), (10, 10, 0), (0, 0, 0)
        insertUsage(usage("2026-01-10T08:00:00Z").customer("customer-1").promoCodes("SUMMER", "VIP")
            .totals(110, 90).subtotals(100, 85).shipping(10, 5));
        insertUsage(usage("2026-01-11T08:00:00Z").promoCodes("SUMMER")
            .totals(110, 100).subtotals(100, 90).shipping(10, 10));
        insertUsage(usage("2026-01-12T08:00:00Z")
            .totals(110, 110).subtotals(100, 100).shipping(10, 10));

        PromotionRuleUsageStatsResponse response = getStats(FROM, TO);

        assertThat(response.totals().usageCount()).isEqualTo(3);
        assertThat(response.totals().enteredPromoCodeCount()).isEqualTo(3);
        assertThat(response.totals().registeredCustomerUsageCount()).isEqualTo(1);
        assertThat(response.totals().anonymousCustomerUsageCount()).isEqualTo(2);
        assertThat(response.totals().averageTotalDiscount()).isEqualByComparingTo("10");
        assertThat(response.totals().averageSubtotalDiscount()).isEqualByComparingTo("8.3333");
        assertThat(response.totals().averageShippingDiscount()).isEqualByComparingTo("1.6667");
    }

    @Test
    @DisplayName("Should not count usages before the range start or at and after its end")
    void getUsageStats_ShouldExcludeUsages_WhenTheyAreOutsideRange() throws Exception {
        insertUsage(usage("2026-01-09T23:59:59Z"));
        insertUsage(usage("2026-01-10T00:00:00Z"));
        insertUsage(usage("2026-01-12T23:59:59Z"));
        insertUsage(usage("2026-01-13T00:00:00Z"));

        PromotionRuleUsageStatsResponse response = getStats(FROM, TO);

        assertThat(response.totals().usageCount()).isEqualTo(2);
        assertThat(response.dailyCounts())
            .extracting(PromotionRuleUsageDailyCountDto::usageCount)
            .containsExactly(1L, 0L, 1L);
    }

    @Test
    @DisplayName("Should count fewer usages when the range moves past some of them")
    void getUsageStats_ShouldCountFewerUsages_WhenRangeStartsAfterSomeOfThem() throws Exception {
        insertUsage(usage("2026-01-10T08:00:00Z"));
        insertUsage(usage("2026-01-11T08:00:00Z"));
        insertUsage(usage("2026-01-12T08:00:00Z"));

        PromotionRuleUsageStatsResponse wholeRange = getStats(FROM, TO);
        PromotionRuleUsageStatsResponse laterRange = getStats(Instant.parse("2026-01-11T12:00:00Z"), TO);

        assertThat(wholeRange.totals().usageCount()).isEqualTo(3);
        assertThat(laterRange.totals().usageCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should count only confirmed usages of the requested storefront")
    void getUsageStats_ShouldCountOnlyUsedUsagesOfStorefront() throws Exception {
        insertUsage(usage("2026-01-10T08:00:00Z"));
        insertUsage(usage("2026-01-10T08:00:00Z").status(PromotionRuleUsageStatus.RESERVED));
        insertUsage(usage("2026-01-10T08:00:00Z").status(PromotionRuleUsageStatus.CANCELED));
        insertUsage(usage("2026-01-10T08:00:00Z").status(PromotionRuleUsageStatus.EXPIRED));
        insertUsage(usage("2026-01-10T08:00:00Z").storefrontId(STOREFRONT_ID + 1));

        PromotionRuleUsageStatsResponse response = getStats(FROM, TO);

        assertThat(response.totals().usageCount()).isEqualTo(1);
        assertThat(response.dailyCounts().getFirst().usageCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should return every day of the range in order, with 0 for days without usage")
    void getUsageStats_ShouldReturnEveryDayInOrder_WithZeroForEmptyDays() throws Exception {
        insertUsage(usage("2026-01-10T00:00:00Z"));
        insertUsage(usage("2026-01-10T23:59:59Z"));
        insertUsage(usage("2026-01-12T10:00:00Z"));

        PromotionRuleUsageStatsResponse response = getStats(FROM, TO);

        assertThat(response.dailyCounts()).containsExactly(
            new PromotionRuleUsageDailyCountDto(LocalDate.parse("2026-01-10"), 2),
            new PromotionRuleUsageDailyCountDto(LocalDate.parse("2026-01-11"), 0),
            new PromotionRuleUsageDailyCountDto(LocalDate.parse("2026-01-12"), 1)
        );
    }

    @Test
    @DisplayName("Should return zero counts, null averages and zero days when there is no usage")
    void getUsageStats_ShouldReturnZeros_WhenThereIsNoUsage() throws Exception {
        PromotionRuleUsageStatsResponse response = getStats(FROM, TO);

        assertThat(response.totals().usageCount()).isZero();
        assertThat(response.totals().enteredPromoCodeCount()).isZero();
        assertThat(response.totals().averageTotalDiscount()).isNull();
        assertThat(response.dailyCounts())
            .extracting(PromotionRuleUsageDailyCountDto::usageCount)
            .containsExactly(0L, 0L, 0L);
    }

    @Test
    @DisplayName("Should return 400 Bad Request when from is after to")
    void getUsageStats_ShouldReturn400_WhenFromIsAfterTo() throws Exception {
        mockMvc.perform(statsRequest(TO, FROM))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors[?(@.path == 'validRange' && @.code == 'ASSERT_TRUE')]").exists());
    }

    @Test
    @DisplayName("Should return 400 Bad Request when from is missing")
    void getUsageStats_ShouldReturn400_WhenFromIsMissing() throws Exception {
        mockMvc.perform(get(STATS_URL)
                .header("X-Storefront-Id", STOREFRONT_ID)
                .param("to", TO.toString()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[?(@.path == 'from' && @.code == 'NOT_NULL')]").exists());
    }

    @Test
    @DisplayName("Should return 400 Bad Request when to is missing")
    void getUsageStats_ShouldReturn400_WhenToIsMissing() throws Exception {
        mockMvc.perform(get(STATS_URL)
                .header("X-Storefront-Id", STOREFRONT_ID)
                .param("from", FROM.toString()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[?(@.path == 'to' && @.code == 'NOT_NULL')]").exists());
    }

    @Test
    @DisplayName("Should return 200 OK with no days when from equals to")
    void getUsageStats_ShouldReturnNoDays_WhenFromEqualsTo() throws Exception {
        insertUsage(usage("2026-01-10T00:00:00Z"));

        PromotionRuleUsageStatsResponse response = getStats(FROM, FROM);

        assertThat(response.totals().usageCount()).isZero();
        assertThat(response.dailyCounts()).isEmpty();
    }

    @Test
    @DisplayName("Should serve a repeated request from the cache, without the usages added since")
    void getUsageStats_ShouldReturnCachedResponse_WhenRequestIsRepeated() throws Exception {
        insertUsage(usage("2026-01-10T08:00:00Z"));
        PromotionRuleUsageStatsResponse first = getStats(FROM, TO);

        insertUsage(usage("2026-01-11T08:00:00Z"));
        PromotionRuleUsageStatsResponse second = getStats(FROM, TO);

        assertThat(second).isEqualTo(first);
        assertThat(second.totals().usageCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should cache each range separately")
    void getUsageStats_ShouldCacheEachRangeSeparately() throws Exception {
        insertUsage(usage("2026-01-10T08:00:00Z"));
        insertUsage(usage("2026-01-11T08:00:00Z"));
        getStats(FROM, TO);

        PromotionRuleUsageStatsResponse otherRange = getStats(Instant.parse("2026-01-11T00:00:00Z"), TO);

        assertThat(otherRange.totals().usageCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should keep the cached response for 30 minutes")
    void getUsageStats_ShouldCacheResponseFor30Minutes() throws Exception {
        getStats(FROM, TO);

        Long ttlSeconds = redisTemplate.getExpire(
            PromotionLookupApiImpl.USAGE_STATS_CACHE_NAME + "::" + STOREFRONT_ID + ":" + FROM + ":" + TO
        );

        assertThat(ttlSeconds).isBetween(Duration.ofMinutes(29).toSeconds(), Duration.ofMinutes(30).toSeconds());
    }

    @Test
    @WithMockJwt(roles = Roles.AUDITOR)
    @DisplayName("Should return 200 OK when the user is an auditor")
    void getUsageStats_ShouldReturn200_WhenUserIsAuditor() throws Exception {
        mockMvc.perform(statsRequest(FROM, TO))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockJwt(roles = {Roles.EMPLOYEE, Roles.INTEGRATION_CLIENT})
    @DisplayName("Should return 403 Forbidden when the user can't read usage stats")
    void getUsageStats_ShouldReturn403_WhenUserHasNoAllowedRole() throws Exception {
        mockMvc.perform(statsRequest(FROM, TO))
            .andExpect(status().isForbidden());
    }

    // --- Helpers ---

    private PromotionRuleUsageStatsResponse getStats(Instant from, Instant to) throws Exception {
        String body = mockMvc.perform(statsRequest(from, to))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper.readValue(body, PromotionRuleUsageStatsResponse.class);
    }

    private static org.springframework.test.web.servlet.RequestBuilder statsRequest(Instant from, Instant to) {
        return get(STATS_URL)
            .header("X-Storefront-Id", STOREFRONT_ID)
            .param("from", from.toString())
            .param("to", to.toString());
    }

    private static UsageRow usage(String createdAt) {
        return new UsageRow(Instant.parse(createdAt));
    }

    private void insertUsage(UsageRow row) {
        UUID usageId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO promotion_rule_usage
                    (id, storefront_id, status, applied_rule_ids, limited_rule_ids, created_at, updated_at)
                VALUES (?, ?, ?, '{}', '{}', ?, ?)
                """,
            usageId, row.storefrontId, row.status.name(), row.timestamp(), row.timestamp());
        jdbcTemplate.update("""
                INSERT INTO promotion_rule_usage_audit
                    (usage_id, storefront_id, customer_id, promo_codes,
                     shipping_cost_before, shipping_cost_after, subtotal_before, subtotal_after,
                     total_before, total_after, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            usageId, row.storefrontId, row.customerId, row.promoCodes,
            row.shippingBefore, row.shippingAfter, row.subtotalBefore, row.subtotalAfter,
            row.totalBefore, row.totalAfter, row.timestamp());
    }

    /**
     * A usage with its audit, confirmed and anonymous by default, with no promo code and no discount.
     */
    private static final class UsageRow {
        private final Instant createdAt;
        private long storefrontId = STOREFRONT_ID;
        private PromotionRuleUsageStatus status = PromotionRuleUsageStatus.USED;
        private String customerId;
        private String[] promoCodes = {};
        private BigDecimal totalBefore = BigDecimal.ZERO;
        private BigDecimal totalAfter = BigDecimal.ZERO;
        private BigDecimal subtotalBefore = BigDecimal.ZERO;
        private BigDecimal subtotalAfter = BigDecimal.ZERO;
        private BigDecimal shippingBefore = BigDecimal.ZERO;
        private BigDecimal shippingAfter = BigDecimal.ZERO;

        private UsageRow(Instant createdAt) {
            this.createdAt = createdAt;
        }

        UsageRow storefrontId(long storefrontId) {
            this.storefrontId = storefrontId;
            return this;
        }

        UsageRow status(PromotionRuleUsageStatus status) {
            this.status = status;
            return this;
        }

        UsageRow customer(String customerId) {
            this.customerId = customerId;
            return this;
        }

        UsageRow promoCodes(String... promoCodes) {
            this.promoCodes = promoCodes;
            return this;
        }

        UsageRow totals(long before, long after) {
            this.totalBefore = BigDecimal.valueOf(before);
            this.totalAfter = BigDecimal.valueOf(after);
            return this;
        }

        UsageRow subtotals(long before, long after) {
            this.subtotalBefore = BigDecimal.valueOf(before);
            this.subtotalAfter = BigDecimal.valueOf(after);
            return this;
        }

        UsageRow shipping(long before, long after) {
            this.shippingBefore = BigDecimal.valueOf(before);
            this.shippingAfter = BigDecimal.valueOf(after);
            return this;
        }

        java.sql.Timestamp timestamp() {
            return java.sql.Timestamp.from(createdAt);
        }
    }
}
