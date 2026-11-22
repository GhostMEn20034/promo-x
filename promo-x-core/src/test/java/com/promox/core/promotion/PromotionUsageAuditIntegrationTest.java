package com.promox.core.promotion;

import com.promox.core.infrastructure.security.Roles;
import com.promox.core.promotion.internal.persistence.PromotionRepository;
import com.promox.core.promotion.internal.persistence.PromotionRule;
import com.promox.core.promotion.internal.persistence.PromotionRuleUsageStatus;
import com.promox.core.promotion.internal.service.action.FreeShippingAction;
import com.promox.core.promotion.internal.service.condition.Operator;
import com.promox.core.promotion.internal.service.condition.PromoCodeConditionNode;
import com.promox.core.shared.IntegrationTestBase;
import com.promox.core.testfixtures.security.WithMockJwt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WithMockJwt(roles = Roles.PROMOTION_MANAGER)
public class PromotionUsageAuditIntegrationTest extends IntegrationTestBase {
    private static final String AUDIT_URL = "/api/promotion/usage/audit/";
    private static final long STOREFRONT_ID = 100L;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PromotionRepository promotionRepository;

    @Test
    @DisplayName("Should return the audits newest first, with the usage status and the matched and applied rules")
    void getUsageAudit_ShouldReturnAuditsWithStatusAndRules() throws Exception {
        PromotionRule low = saveRule("Low", 10);
        PromotionRule high = saveRule("High", 100);
        PromotionRule middle = saveRule("Middle", 50);
        // Applied middle, low, high: neither creation (ID) order nor priority order, either way round
        UUID newest = insertAudit(audit("2026-01-11T08:00:00Z")
            .status(PromotionRuleUsageStatus.RESERVED)
            .customer("customer-1")
            .promoCodes("SUMMER", "VIP")
            .totals(110, 90)
            .matched(low, high, middle)
            .applied(middle, low, high));
        UUID oldest = insertAudit(audit("2026-01-10T08:00:00Z").matched(middle).applied(middle));

        mockMvc.perform(auditRequest())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content[*].usageId").value(contains(newest.toString(), oldest.toString())))
            .andExpect(jsonPath("$.content[0].status").value("RESERVED"))
            .andExpect(jsonPath("$.content[0].customerId").value("customer-1"))
            .andExpect(jsonPath("$.content[0].sessionId").doesNotExist())
            .andExpect(jsonPath("$.content[0].promoCodes").value(containsInAnyOrder("SUMMER", "VIP")))
            .andExpect(jsonPath("$.content[0].totalBefore").value(110.0))
            .andExpect(jsonPath("$.content[0].totalAfter").value(90.0))
            .andExpect(jsonPath("$.content[0].createdAt").value("2026-01-11T08:00:00Z"))
            .andExpect(jsonPath("$.content[0].matchedRules[*].ruleName").value(contains("High", "Middle", "Low")))
            .andExpect(jsonPath("$.content[0].matchedRules[0].ruleId").value(high.getId()))
            .andExpect(jsonPath("$.content[0].appliedRules[*].ruleName").value(contains("Middle", "Low", "High")))
            .andExpect(jsonPath("$.content[1].status").value("USED"))
            .andExpect(jsonPath("$.content[1].appliedRules[*].ruleId").value(contains(middle.getId().intValue())));
    }

    @Test
    @DisplayName("Should order each audit's applied rules as its usage recorded them, not by anything of the rules")
    void getUsageAudit_ShouldOrderAppliedRulesByEachUsagesRecordedOrder() throws Exception {
        PromotionRule a = saveRule("A", 30);
        PromotionRule b = saveRule("B", 10);
        PromotionRule c = saveRule("C", 40);
        PromotionRule d = saveRule("D", 20);
        // The same rules in every audit, so sorting by a column of the rules would order all three alike. The second
        // order is the first reversed. insertAudit adds the join rows by ID every time, so only applied_rule_ids
        // tells these orders apart.
        insertAudit(audit("2026-01-12T08:00:00Z").applied(c, a, d, b));
        insertAudit(audit("2026-01-11T08:00:00Z").applied(b, d, a, c));
        insertAudit(audit("2026-01-10T08:00:00Z").applied(a, c, b, d));

        mockMvc.perform(auditRequest())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].appliedRules[*].ruleName").value(contains("C", "A", "D", "B")))
            .andExpect(jsonPath("$.content[1].appliedRules[*].ruleName").value(contains("B", "D", "A", "C")))
            .andExpect(jsonPath("$.content[2].appliedRules[*].ruleName").value(contains("A", "C", "B", "D")));
    }

    @Test
    @DisplayName("Should keep the audits that matched any of the given rules")
    void getUsageAudit_ShouldFilterByMatchedRules() throws Exception {
        PromotionRule first = saveRule("First", 10);
        PromotionRule second = saveRule("Second", 20);
        PromotionRule other = saveRule("Other", 30);
        UUID matchedFirst = insertAudit(audit("2026-01-10T08:00:00Z").matched(first));
        UUID matchedSecond = insertAudit(audit("2026-01-11T08:00:00Z").matched(second, other));
        insertAudit(audit("2026-01-12T08:00:00Z").matched(other));

        mockMvc.perform(auditRequest().param("matchedRuleIds", first.getId() + "," + second.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content[*].usageId")
                .value(contains(matchedSecond.toString(), matchedFirst.toString())))
            // The filter keeps an audit, not just the rules it names
            .andExpect(jsonPath("$.content[0].matchedRules[*].ruleName").value(contains("Other", "Second")));
    }

    @Test
    @DisplayName("Should keep the audits that applied any of the given rules, given as repeated parameters")
    void getUsageAudit_ShouldFilterByAppliedRules() throws Exception {
        PromotionRule applied = saveRule("Applied", 10);
        PromotionRule onlyMatched = saveRule("Only matched", 20);
        UUID kept = insertAudit(audit("2026-01-10T08:00:00Z").matched(applied).applied(applied));
        insertAudit(audit("2026-01-11T08:00:00Z").matched(onlyMatched));

        mockMvc.perform(auditRequest()
                .param("appliedRuleIds", String.valueOf(applied.getId()))
                .param("appliedRuleIds", String.valueOf(onlyMatched.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].usageId").value(contains(kept.toString())));
    }

    @Test
    @DisplayName("Should keep only the audits that pass both filters when both are given")
    void getUsageAudit_ShouldCombineFilters() throws Exception {
        PromotionRule rule = saveRule("Rule", 10);
        PromotionRule other = saveRule("Other", 20);
        UUID both = insertAudit(audit("2026-01-10T08:00:00Z").matched(rule, other).applied(other));
        insertAudit(audit("2026-01-11T08:00:00Z").matched(rule).applied(rule));
        insertAudit(audit("2026-01-12T08:00:00Z").matched(other).applied(other));

        mockMvc.perform(auditRequest()
                .param("matchedRuleIds", String.valueOf(rule.getId()))
                .param("appliedRuleIds", String.valueOf(other.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].usageId").value(contains(both.toString())));
    }

    @Test
    @DisplayName("Should page the storefront's audits only, and sort them oldest first on request")
    void getUsageAudit_ShouldPageAndSortTheStorefrontsAudits() throws Exception {
        UUID first = insertAudit(audit("2026-01-10T08:00:00Z"));
        UUID second = insertAudit(audit("2026-01-11T08:00:00Z"));
        UUID third = insertAudit(audit("2026-01-12T08:00:00Z"));
        insertAudit(audit("2026-01-13T08:00:00Z").storefrontId(STOREFRONT_ID + 1));

        mockMvc.perform(auditRequest().param("page", "2").param("size", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.content[*].usageId").value(contains(first.toString())));

        mockMvc.perform(auditRequest().param("sort", "createdAt,asc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].usageId")
                .value(contains(first.toString(), second.toString(), third.toString())));
    }

    @Test
    @DisplayName("Should return empty rule lists and an empty page when there is nothing to show")
    void getUsageAudit_ShouldReturnEmptyLists_WhenThereAreNoRulesOrAudits() throws Exception {
        insertAudit(audit("2026-01-10T08:00:00Z"));

        mockMvc.perform(auditRequest())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].matchedRules").isEmpty())
            .andExpect(jsonPath("$.content[0].appliedRules").isEmpty());

        mockMvc.perform(auditRequest().param("matchedRuleIds", "999999"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0))
            .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    @DisplayName("Should return 409 Conflict when sorted by anything but createdAt")
    void getUsageAudit_ShouldReturn409_WhenSortPropertyIsNotAllowed() throws Exception {
        mockMvc.perform(auditRequest().param("sort", "totalAfter,desc"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INVALID_SORT_PROPERTY"))
            .andExpect(jsonPath("$.errors[0].errorContext.invalidProperty").value("totalAfter"));
    }

    @Test
    @DisplayName("Should return 400 Bad Request when a rule ID isn't a number")
    void getUsageAudit_ShouldReturn400_WhenRuleIdIsNotANumber() throws Exception {
        mockMvc.perform(auditRequest().param("matchedRuleIds", "abc"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockJwt(roles = Roles.ADMIN)
    @DisplayName("Should return 200 OK when the user is an admin")
    void getUsageAudit_ShouldReturn200_WhenUserIsAdmin() throws Exception {
        mockMvc.perform(auditRequest())
            .andExpect(status().isOk());
    }

    @Test
    @WithMockJwt(roles = Roles.AUDITOR)
    @DisplayName("Should return 200 OK when the user is an auditor")
    void getUsageAudit_ShouldReturn200_WhenUserIsAuditor() throws Exception {
        mockMvc.perform(auditRequest())
            .andExpect(status().isOk());
    }

    @Test
    @WithMockJwt(roles = {Roles.EMPLOYEE, Roles.INTEGRATION_CLIENT})
    @DisplayName("Should return 403 Forbidden when the user can't read the usage audit")
    void getUsageAudit_ShouldReturn403_WhenUserHasNoAllowedRole() throws Exception {
        mockMvc.perform(auditRequest())
            .andExpect(status().isForbidden());
    }

    // --- Helpers ---

    private static MockHttpServletRequestBuilder auditRequest() {
        return get(AUDIT_URL).header("X-Storefront-Id", STOREFRONT_ID);
    }

    private PromotionRule saveRule(String name, int priority) {
        PromotionRule rule = new PromotionRule();
        rule.setStorefrontId(STOREFRONT_ID);
        rule.setName(name);
        rule.setPriority(priority);
        rule.setShortCircuit(false);
        rule.setActive(true);
        rule.setConditionTree(new PromoCodeConditionNode("SUMMER", Operator.EQUALS));
        rule.setAction(new FreeShippingAction());
        return promotionRepository.saveAndFlush(rule);
    }

    private static AuditRow audit(String createdAt) {
        return new AuditRow(Instant.parse(createdAt));
    }

    private UUID insertAudit(AuditRow row) {
        UUID usageId = UUID.randomUUID();
        Long[] appliedIds = row.applied.stream().map(PromotionRule::getId).toArray(Long[]::new);
        jdbcTemplate.update("""
                INSERT INTO promotion_rule_usage
                    (id, storefront_id, status, applied_rule_ids, limited_rule_ids, created_at, updated_at)
                VALUES (?, ?, ?, ?, '{}', ?, ?)
                """,
            usageId, row.storefrontId, row.status.name(), appliedIds, row.timestamp(), row.timestamp());
        jdbcTemplate.update("""
                INSERT INTO promotion_rule_usage_audit
                    (usage_id, storefront_id, customer_id, promo_codes,
                     shipping_cost_before, shipping_cost_after, subtotal_before, subtotal_after,
                     total_before, total_after, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            usageId, row.storefrontId, row.customerId, row.promoCodes,
            BigDecimal.ZERO, BigDecimal.ZERO, row.totalBefore, row.totalAfter,
            row.totalBefore, row.totalAfter, row.timestamp());
        for (PromotionRule rule : row.matched) {
            jdbcTemplate.update(
                "INSERT INTO promotion_rule_usage_audit_matched_rule (usage_id, rule_id) VALUES (?, ?)",
                usageId, rule.getId());
        }
        // By ID whatever the applied order, so that order is only in applied_rule_ids, never in the insertion order
        List<PromotionRule> appliedById = row.applied.stream()
            .sorted(Comparator.comparing(PromotionRule::getId))
            .toList();
        for (PromotionRule rule : appliedById) {
            jdbcTemplate.update(
                "INSERT INTO promotion_rule_usage_audit_applied_rule (usage_id, rule_id) VALUES (?, ?)",
                usageId, rule.getId());
        }
        return usageId;
    }

    /**
     * A usage with its audit: confirmed, anonymous, without promo codes, rules or discount by default.
     */
    private static final class AuditRow {
        private final Instant createdAt;
        private long storefrontId = STOREFRONT_ID;
        private PromotionRuleUsageStatus status = PromotionRuleUsageStatus.USED;
        private String customerId;
        private String[] promoCodes = {};
        private BigDecimal totalBefore = BigDecimal.ZERO;
        private BigDecimal totalAfter = BigDecimal.ZERO;
        private List<PromotionRule> matched = List.of();
        private List<PromotionRule> applied = List.of();

        private AuditRow(Instant createdAt) {
            this.createdAt = createdAt;
        }

        AuditRow storefrontId(long storefrontId) {
            this.storefrontId = storefrontId;
            return this;
        }

        AuditRow status(PromotionRuleUsageStatus status) {
            this.status = status;
            return this;
        }

        AuditRow customer(String customerId) {
            this.customerId = customerId;
            return this;
        }

        AuditRow promoCodes(String... promoCodes) {
            this.promoCodes = promoCodes;
            return this;
        }

        AuditRow totals(long before, long after) {
            this.totalBefore = BigDecimal.valueOf(before);
            this.totalAfter = BigDecimal.valueOf(after);
            return this;
        }

        /** In the order they matched; the response orders them by priority. */
        AuditRow matched(PromotionRule... rules) {
            this.matched = List.of(rules);
            return this;
        }

        /** In the order they were applied, which the response keeps. */
        AuditRow applied(PromotionRule... rules) {
            this.applied = List.of(rules);
            return this;
        }

        java.sql.Timestamp timestamp() {
            return java.sql.Timestamp.from(createdAt);
        }
    }
}
