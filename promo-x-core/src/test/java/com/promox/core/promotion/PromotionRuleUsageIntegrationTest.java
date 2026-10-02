package com.promox.core.promotion;

import com.promox.core.category.internal.persistence.Category;
import com.promox.core.category.internal.persistence.CategoryRepository;
import com.promox.core.product.internal.persistence.Product;
import com.promox.core.product.internal.persistence.ProductRepository;
import com.promox.core.promotion.internal.persistence.PromotionRepository;
import com.promox.core.promotion.internal.persistence.PromotionRule;
import com.promox.core.promotion.internal.persistence.PromotionRuleUsage;
import com.promox.core.promotion.internal.persistence.PromotionRuleUsageAuditProduct;
import com.promox.core.promotion.internal.persistence.PromotionRuleUsageAuditProductRepository;
import com.promox.core.promotion.internal.persistence.PromotionRuleUsageRepository;
import com.promox.core.promotion.internal.persistence.PromotionRuleUsageStatus;
import com.promox.core.promotion.internal.service.PromotionRuleCacheReader;
import com.promox.core.promotion.internal.service.PromotionRuleUsageService;
import com.promox.core.promotion.internal.service.action.ActionStrategy;
import com.promox.core.promotion.internal.service.action.FreeShippingAction;
import com.promox.core.promotion.internal.service.action.ProductBuyXGetYFreeAction;
import com.promox.core.promotion.internal.service.action.ProductPercentageDiscountAction;
import com.promox.core.promotion.internal.service.condition.Operator;
import com.promox.core.promotion.internal.service.condition.PromoCodeConditionNode;
import com.promox.core.shared.IntegrationTestBase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.EnableScenarios;
import org.springframework.modulith.test.Scenario;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Each test evaluates a storefront at most once, so every rule lookup is a cache miss.
@EnableScenarios
public class PromotionRuleUsageIntegrationTest extends IntegrationTestBase {
    private static final String EVALUATE_URL_PATH = "/promotion/evaluate";
    private static final String CONFIRM_URL_PATH = "/promotion/usage/{usageId}/confirm";
    private static final String CANCEL_URL_PATH = "/promotion/usage/{usageId}/cancel";

    private static final Long STOREFRONT_ID = 100L;
    private static final String KEYBOARD = "PROD-KB-01";
    private static final String MOUSE = "PROD-MS-01";

    private static final String COUNTER_KEY_PREFIX = "promotion-rule:remaining-uses:";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PromotionRepository promotionRepository;

    @Autowired
    private PromotionRuleUsageRepository usageRepository;

    @Autowired
    private PromotionRuleUsageAuditProductRepository auditProductRepository;

    @Autowired
    private PromotionRuleUsageService promotionRuleUsageService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        Category keyboards = categoryRepository.save(
            Category.builder()
                .externalId("CAT-KEYBOARDS")
                .name("Keyboards")
                .storefrontId(STOREFRONT_ID)
                .build()
        );

        productRepository.saveAll(List.of(
            product(KEYBOARD, "SKU-KB-01", Set.of(keyboards.getId())),
            product(MOUSE, "SKU-MS-01", Set.of())
        ));
    }

    @Nested
    @DisplayName("Usage Creation Tests")
    class UsageCreationTests {

        @Test
        @DisplayName("Should reserve the usage and take one use when a limited rule is applied")
        void evaluate_limitedRule_reservesUsage() throws Exception {
            PromotionRule limited = saveLimitedRule("20% off keyboards", 100, 5, keyboardDiscount());

            JsonNode usageNode = evaluate(null).path("usage");

            assertThat(usageNode.path("status").asString()).isEqualTo("RESERVED");
            assertThat(usageNode.path("expiresAt").isNull()).isFalse();

            PromotionRuleUsage usage = usageRepository.findById(usageId(usageNode)).orElseThrow();
            assertThat(usage.getStatus()).isEqualTo(PromotionRuleUsageStatus.RESERVED);
            assertThat(usage.getStorefrontId()).isEqualTo(STOREFRONT_ID);
            assertThat(usage.getAppliedRuleIds()).containsExactly(limited.getId());
            assertThat(usage.getLimitedRuleIds()).containsExactly(limited.getId());
            assertThat(usage.getExpiresAt()).isAfter(usage.getCreatedAt());
            assertThat(usage.getExpiredAt()).isNull();

            assertThat(counter(limited)).isEqualTo("4");
        }

        @Test
        @DisplayName("Should mark the usage as used right away when reservation is switched off")
        void evaluate_reserveUsageFalse_marksUsageUsed() throws Exception {
            PromotionRule limited = saveLimitedRule("20% off keyboards", 100, 5, keyboardDiscount());

            JsonNode usageNode = evaluate(false).path("usage");

            assertThat(usageNode.path("status").asString()).isEqualTo("USED");
            assertThat(usageNode.path("expiresAt").isNull()).isTrue();

            PromotionRuleUsage usage = usageRepository.findById(usageId(usageNode)).orElseThrow();
            assertThat(usage.getStatus()).isEqualTo(PromotionRuleUsageStatus.USED);
            assertThat(usage.getLimitedRuleIds()).containsExactly(limited.getId());
            assertThat(usage.getExpiresAt()).isNull();

            assertThat(counter(limited)).isEqualTo("4");
        }

        @Test
        @DisplayName("Should mark the usage as used when only rules without usage limits are applied")
        void evaluate_onlyUnlimitedRules_marksUsageUsed() throws Exception {
            PromotionRule unlimited = saveRule("Free shipping", 100, new FreeShippingAction());

            JsonNode usageNode = evaluate(true).path("usage");

            assertThat(usageNode.path("status").asString()).isEqualTo("USED");

            PromotionRuleUsage usage = usageRepository.findById(usageId(usageNode)).orElseThrow();
            assertThat(usage.getAppliedRuleIds()).containsExactly(unlimited.getId());
            assertThat(usage.getLimitedRuleIds()).isEmpty();
            assertThat(usage.getExpiresAt()).isNull();
            assertThat(counter(unlimited)).isNull();
        }

        @Test
        @DisplayName("Should record all applied rules in order but hold a use only for the limited ones")
        void evaluate_limitedAndUnlimitedRules_recordsBoth() throws Exception {
            PromotionRule unlimited = saveRule("Free shipping", 10, new FreeShippingAction());
            PromotionRule limited = saveLimitedRule("20% off keyboards", 100, 5, keyboardDiscount());

            JsonNode usageNode = evaluate(null).path("usage");

            assertThat(usageNode.path("status").asString()).isEqualTo("RESERVED");

            PromotionRuleUsage usage = usageRepository.findById(usageId(usageNode)).orElseThrow();
            assertThat(usage.getAppliedRuleIds()).containsExactly(limited.getId(), unlimited.getId());
            assertThat(usage.getLimitedRuleIds()).containsExactly(limited.getId());
        }

        @Test
        @DisplayName("Should not create a usage when no rule is applied")
        void evaluate_noRuleApplied_createsNoUsage() throws Exception {
            JsonNode response = evaluate(null);

            assertThat(response.path("usage").isNull()).isTrue();
            assertThat(usageRepository.count()).isZero();
        }

        @Test
        @DisplayName("Should match but not apply a limited rule that has no uses left")
        void evaluate_exhaustedRule_isNotApplied() throws Exception {
            PromotionRule exhausted = saveLimitedRule("20% off keyboards", 100, 0, keyboardDiscount());

            JsonNode response = evaluate(null);

            assertThat(ruleIds(response, "matchedRules")).containsExactly(exhausted.getId());
            assertThat(response.path("appliedRules")).isEmpty();
            assertThat(response.path("totalDiscount").decimalValue()).isEqualByComparingTo("0.00");
            assertThat(response.path("usage").isNull()).isTrue();
            assertThat(usageRepository.count()).isZero();
            assertThat(counter(exhausted)).isEqualTo("0");
        }

        @Test
        @DisplayName("Should reject a limited rule whose Redis counter is 0, even if the database still shows uses left")
        void evaluate_redisCounterAtZero_rejectsRule() throws Exception {
            PromotionRule limited = saveLimitedRule("20% off keyboards", 100, 5, keyboardDiscount());
            redisTemplate.opsForValue().set(COUNTER_KEY_PREFIX + limited.getId(), "0");

            JsonNode response = evaluate(null);

            assertThat(ruleIds(response, "matchedRules")).containsExactly(limited.getId());
            assertThat(response.path("appliedRules")).isEmpty();
            assertThat(response.path("totalDiscount").decimalValue()).isEqualByComparingTo("0.00");
            assertThat(response.path("usage").isNull()).isTrue();
            assertThat(counter(limited)).isEqualTo("0");
            assertThat(remainingUses(limited)).isEqualTo(5);
        }

        @Test
        @DisplayName("Should apply a limited rule on its last remaining use")
        void evaluate_lastRemainingUse_isApplied() throws Exception {
            PromotionRule limited = saveLimitedRule("20% off keyboards", 100, 1, keyboardDiscount());

            JsonNode response = evaluate(null);

            assertThat(ruleIds(response, "appliedRules")).containsExactly(limited.getId());
            assertThat(counter(limited)).isEqualTo("0");
        }

        @Test
        @DisplayName("Should give the use back when a limited rule matches but its action changes nothing")
        void evaluate_limitedRuleNotApplicable_keepsItsUses() throws Exception {
            // The cart has a single mouse, so buy 2 get 1 free can't apply.
            PromotionRule notApplicable = saveLimitedRule(
                "Mouse 2+1", 100, 5, new ProductBuyXGetYFreeAction(MOUSE, 2, 1)
            );

            JsonNode response = evaluate(null);

            assertThat(ruleIds(response, "matchedRules")).containsExactly(notApplicable.getId());
            assertThat(response.path("appliedRules")).isEmpty();
            assertThat(counter(notApplicable)).isEqualTo("5");
        }
    }

    @Nested
    @DisplayName("Usage Audit Tests")
    class UsageAuditTests {

        @Test
        @DisplayName("Should record order values before and after, the matched and applied rules, and the order lines")
        void evaluate_appliedRules_recordsAudit() throws Exception {
            PromotionRule keyboardRule = saveLimitedRule("20% off keyboards", 100, 5, keyboardDiscount());
            PromotionRule shippingRule = saveRule("Free shipping", 50, new FreeShippingAction());
            PromotionRule matchedOnly = saveRule("Mouse 2+1", 10, new ProductBuyXGetYFreeAction(MOUSE, 2, 1));

            UUID usageId = usageId(evaluate(null).path("usage"));

            Map<String, Object> audit = jdbcTemplate.queryForMap(
                "SELECT * FROM promotion_rule_usage_audit WHERE usage_id = ?", usageId
            );
            assertThat(audit.get("storefront_id")).isEqualTo(STOREFRONT_ID);
            assertThat(audit.get("session_id")).isEqualTo("sess-1");
            assertThat(audit.get("customer_id")).isEqualTo("cust-1");
            assertThat((BigDecimal) audit.get("subtotal_before")).isEqualByComparingTo("250.00");
            assertThat((BigDecimal) audit.get("subtotal_after")).isEqualByComparingTo("210.00");
            assertThat((BigDecimal) audit.get("shipping_cost_before")).isEqualByComparingTo("15.00");
            assertThat((BigDecimal) audit.get("shipping_cost_after")).isEqualByComparingTo("0.00");
            assertThat((BigDecimal) audit.get("total_before")).isEqualByComparingTo("265.00");
            assertThat((BigDecimal) audit.get("total_after")).isEqualByComparingTo("210.00");

            assertThat(auditRuleIds("promotion_rule_usage_audit_applied_rule", usageId))
                .containsExactlyInAnyOrder(keyboardRule.getId(), shippingRule.getId());
            assertThat(auditRuleIds("promotion_rule_usage_audit_matched_rule", usageId))
                .containsExactlyInAnyOrder(keyboardRule.getId(), shippingRule.getId(), matchedOnly.getId());

            List<PromotionRuleUsageAuditProduct> products = auditProductRepository.findAll();
            assertThat(products).hasSize(2);
            assertThat(products).allSatisfy(product -> {
                assertThat(product.getRuleUsageAuditId()).isEqualTo(usageId);
                assertThat(product.getStorefrontId()).isEqualTo(STOREFRONT_ID);
            });

            PromotionRuleUsageAuditProduct keyboard = auditProduct(products, KEYBOARD);
            assertThat(keyboard.getProductSku()).isEqualTo("SKU-KB-01");
            assertThat(keyboard.getQuantity()).isEqualTo(2);
            assertThat(keyboard.getUnitPrice()).isEqualByComparingTo("100.00");
            assertThat(keyboard.getCategories()).containsExactly("CAT-KEYBOARDS");

            assertThat(auditProduct(products, MOUSE).getCategories()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Usage Confirmation Tests")
    class UsageConfirmationTests {

        @Test
        @DisplayName("Should mark a reserved usage as used and keep the use taken")
        void confirm_reservedUsage_marksUsed() throws Exception {
            PromotionRule limited = saveLimitedRule("20% off keyboards", 100, 5, keyboardDiscount());
            UUID usageId = usageId(evaluate(null).path("usage"));

            mockMvc.perform(post(CONFIRM_URL_PATH, usageId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(usageId.toString()))
                .andExpect(jsonPath("$.status").value("USED"));

            assertThat(usageStatus(usageId)).isEqualTo(PromotionRuleUsageStatus.USED);
            assertThat(counter(limited)).isEqualTo("4");
        }

        @Test
        @DisplayName("Should fail with 409 Conflict when the usage is already confirmed")
        void confirm_alreadyUsedUsage_returnsConflict() throws Exception {
            saveLimitedRule("20% off keyboards", 100, 5, keyboardDiscount());
            UUID usageId = usageId(evaluate(null).path("usage"));
            mockMvc.perform(post(CONFIRM_URL_PATH, usageId)).andExpect(status().isOk());

            mockMvc.perform(post(CONFIRM_URL_PATH, usageId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROMOTION_RULE_USAGE_NOT_RESERVED"))
                .andExpect(jsonPath("$.errors[0].errorContext.status").value("USED"));
        }

        @Test
        @DisplayName("Should fail with 409 Conflict when the usage was created as used, without a reservation")
        void confirm_usageCreatedAsUsed_returnsConflict() throws Exception {
            saveLimitedRule("20% off keyboards", 100, 5, keyboardDiscount());
            UUID usageId = usageId(evaluate(false).path("usage"));

            mockMvc.perform(post(CONFIRM_URL_PATH, usageId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROMOTION_RULE_USAGE_NOT_RESERVED"));
        }

        @Test
        @DisplayName("Should return 404 Not Found when the usage does not exist")
        void confirm_unknownUsage_returnsNotFound() throws Exception {
            mockMvc.perform(post(CONFIRM_URL_PATH, UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROMOTION_RULE_USAGE_NOT_FOUND"));
        }

        @Test
        @DisplayName("Should return 400 Bad Request when the usage ID is not a UUID")
        void confirm_malformedUsageId_returnsBadRequest() throws Exception {
            mockMvc.perform(post(CONFIRM_URL_PATH, "not-a-uuid"))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Usage Cancellation Tests")
    class UsageCancellationTests {

        @Test
        @DisplayName("Should cancel a reserved usage and give the use back in Redis and in the database")
        void cancel_reservedUsage_restoresUses(Scenario scenario) {
            PromotionRule limited = saveLimitedRule("20% off keyboards", 100, 5, keyboardDiscount());

            // The persisted counter is reduced by the applied-rules listener after the evaluation commits.
            scenario.stimulate(() -> usageId(evaluateUnchecked(null).path("usage")))
                .andWaitForStateChange(() -> remainingUses(limited), remaining -> remaining == 4)
                .andVerify((remaining, usageId) -> {
                    assertThat(counter(limited)).isEqualTo("4");

                    cancelOk(usageId);

                    assertThat(usageStatus(usageId)).isEqualTo(PromotionRuleUsageStatus.CANCELED);
                    assertThat(counter(limited)).isEqualTo("5");
                    assertThat(remainingUses(limited)).isEqualTo(5);
                });
        }

        @Test
        @DisplayName("Should fail with 409 Conflict and keep the use taken when the usage is already confirmed")
        void cancel_confirmedUsage_returnsConflict() throws Exception {
            PromotionRule limited = saveLimitedRule("20% off keyboards", 100, 5, keyboardDiscount());
            UUID usageId = usageId(evaluate(null).path("usage"));
            mockMvc.perform(post(CONFIRM_URL_PATH, usageId)).andExpect(status().isOk());

            mockMvc.perform(post(CANCEL_URL_PATH, usageId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROMOTION_RULE_USAGE_NOT_RESERVED"));

            assertThat(usageStatus(usageId)).isEqualTo(PromotionRuleUsageStatus.USED);
            assertThat(counter(limited)).isEqualTo("4");
        }

        @Test
        @DisplayName("Should give the use back only once when a usage is canceled twice")
        void cancel_alreadyCanceledUsage_returnsConflict() throws Exception {
            PromotionRule limited = saveLimitedRule("20% off keyboards", 100, 5, keyboardDiscount());
            UUID usageId = usageId(evaluate(null).path("usage"));
            mockMvc.perform(post(CANCEL_URL_PATH, usageId)).andExpect(status().isOk());

            mockMvc.perform(post(CANCEL_URL_PATH, usageId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].errorContext.status").value("CANCELED"));

            assertThat(counter(limited)).isEqualTo("5");
        }

        @Test
        @DisplayName("Should return 404 Not Found when the usage does not exist")
        void cancel_unknownUsage_returnsNotFound() throws Exception {
            mockMvc.perform(post(CANCEL_URL_PATH, UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROMOTION_RULE_USAGE_NOT_FOUND"));
        }
    }

    // These call the expiration logic directly instead of waiting for the once-a-minute job.
    @Nested
    @DisplayName("Usage Expiration Tests")
    class UsageExpirationTests {

        @Test
        @DisplayName("Should expire an overdue reserved usage and give the use back in Redis and in the database")
        void expire_overdueReservedUsage_expiresAndRestoresUses(Scenario scenario) {
            PromotionRule limited = saveLimitedRule("20% off keyboards", 100, 5, keyboardDiscount());
            UUID usageId = evaluateAndAwaitUseTaken(scenario, limited);
            makeOverdue(usageId);

            // The database counter is only restored by the listener of PromotionRuleUsageExpiredEvent.
            scenario.stimulate(() -> promotionRuleUsageService.expireOverdueUsages(100))
                .andWaitForStateChange(() -> remainingUses(limited), remaining -> remaining == 5)
                .andVerify(remaining -> {
                    PromotionRuleUsage usage = usageRepository.findById(usageId).orElseThrow();
                    assertThat(usage.getStatus()).isEqualTo(PromotionRuleUsageStatus.EXPIRED);
                    assertThat(usage.getExpiredAt()).isNotNull();
                    assertThat(counter(limited)).isEqualTo("5");
                });
        }

        @Test
        @DisplayName("Should leave a reserved usage alone while its deadline has not passed")
        void expire_reservedUsageNotOverdue_staysReserved() throws Exception {
            PromotionRule limited = saveLimitedRule("20% off keyboards", 100, 5, keyboardDiscount());
            UUID usageId = usageId(evaluate(null).path("usage"));

            promotionRuleUsageService.expireOverdueUsages(100);

            PromotionRuleUsage usage = usageRepository.findById(usageId).orElseThrow();
            assertThat(usage.getStatus()).isEqualTo(PromotionRuleUsageStatus.RESERVED);
            assertThat(usage.getExpiredAt()).isNull();
            assertThat(counter(limited)).isEqualTo("4");
        }

        @Test
        @DisplayName("Should not expire a usage that was confirmed, even after its deadline")
        void expire_confirmedUsage_staysUsed() throws Exception {
            PromotionRule limited = saveLimitedRule("20% off keyboards", 100, 5, keyboardDiscount());
            UUID usageId = usageId(evaluate(null).path("usage"));
            mockMvc.perform(post(CONFIRM_URL_PATH, usageId)).andExpect(status().isOk());
            makeOverdue(usageId);

            promotionRuleUsageService.expireOverdueUsages(100);

            assertThat(usageStatus(usageId)).isEqualTo(PromotionRuleUsageStatus.USED);
            assertThat(counter(limited)).isEqualTo("4");
        }

        @Test
        @DisplayName("Should give the use back only once when expiration runs again")
        void expire_alreadyExpiredUsage_isNotReleasedTwice() throws Exception {
            PromotionRule limited = saveLimitedRule("20% off keyboards", 100, 5, keyboardDiscount());
            UUID usageId = usageId(evaluate(null).path("usage"));
            makeOverdue(usageId);

            promotionRuleUsageService.expireOverdueUsages(100);
            promotionRuleUsageService.expireOverdueUsages(100);

            assertThat(usageStatus(usageId)).isEqualTo(PromotionRuleUsageStatus.EXPIRED);
            assertThat(counter(limited)).isEqualTo("5");
        }

        @Test
        @DisplayName("Should fail with 409 Conflict when confirming or canceling an expired usage")
        void confirmOrCancel_expiredUsage_returnsConflict() throws Exception {
            saveLimitedRule("20% off keyboards", 100, 5, keyboardDiscount());
            UUID usageId = usageId(evaluate(null).path("usage"));
            makeOverdue(usageId);
            promotionRuleUsageService.expireOverdueUsages(100);

            mockMvc.perform(post(CONFIRM_URL_PATH, usageId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].errorContext.status").value("EXPIRED"));
            mockMvc.perform(post(CANCEL_URL_PATH, usageId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].errorContext.status").value("EXPIRED"));
        }
    }

    @Nested
    @DisplayName("Usage Limit Change Tests")
    class UsageLimitChangeTests {

        @Test
        @DisplayName("Should evict the cached rules and drop the Redis counter when a rule's usage limits change")
        void updateUsageLimits_evictsRulesCacheAndResetsCounter() throws Exception {
            PromotionRule limited = saveLimitedRule("20% off keyboards", 100, 5, keyboardDiscount());
            evaluate(null);
            assertThat(redisTemplate.hasKey(rulesCacheKey())).isTrue();
            assertThat(counter(limited)).isEqualTo("4");

            mockMvc.perform(patch("/promotion/{promotionRuleId}/usage-limits", limited.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"hasUsageLimit": false, "mutateRemainingUses": false}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasUsageLimit").value(false));

            assertThat(redisTemplate.hasKey(rulesCacheKey())).isFalse();
            assertThat(counter(limited)).isNull();
        }
    }

    /**
     * Evaluates a cart of 2 keyboards @ 100.00 + 1 mouse @ 50.00 with 15.00 shipping and promo code SUMMER.
     *
     * @param reserveUsage value of the {@code reserveUsage} flag; null leaves it out of the request
     */
    private JsonNode evaluate(Boolean reserveUsage) throws Exception {
        String reserveUsageField = reserveUsage == null ? "" : ", \"reserveUsage\": " + reserveUsage;
        String payload = """
            {
              "sessionId": "sess-1",
              "customerId": "cust-1",
              "promoCodes": ["SUMMER"],
              "items": [
                {"productExternalId": "%s", "quantity": 2, "unitPrice": 100.00},
                {"productExternalId": "%s", "quantity": 1, "unitPrice": 50.00}
              ],
              "shippingCost": 15.00%s
            }
            """.formatted(KEYBOARD, MOUSE, reserveUsageField);

        MvcResult result = mockMvc.perform(post(EVALUATE_URL_PATH)
                .header("X-Storefront-Id", STOREFRONT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode evaluateUnchecked(Boolean reserveUsage) {
        try {
            return evaluate(reserveUsage);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Evaluates the cart and waits until the applied-rules listener has reduced the rule's
     * persisted remaining uses from 5 to 4.
     */
    private UUID evaluateAndAwaitUseTaken(Scenario scenario, PromotionRule limited) {
        AtomicReference<UUID> usageId = new AtomicReference<>();

        scenario.stimulate(() -> usageId(evaluateUnchecked(null).path("usage")))
            .andWaitForStateChange(() -> remainingUses(limited), remaining -> remaining == 4)
            .andVerify((remaining, id) -> usageId.set(id));

        return usageId.get();
    }

    private void makeOverdue(UUID usageId) {
        PromotionRuleUsage usage = usageRepository.findById(usageId).orElseThrow();
        usage.setExpiresAt(Instant.now().minusSeconds(60));
        usageRepository.saveAndFlush(usage);
    }

    private void cancelOk(UUID usageId) {
        try {
            mockMvc.perform(post(CANCEL_URL_PATH, usageId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static ActionStrategy keyboardDiscount() {
        return new ProductPercentageDiscountAction(KEYBOARD, new BigDecimal("0.20"));
    }

    private PromotionRule saveRule(String name, int priority, ActionStrategy action) {
        return promotionRepository.saveAndFlush(newRule(name, priority, action));
    }

    private PromotionRule saveLimitedRule(String name, int priority, int remainingUses, ActionStrategy action) {
        PromotionRule rule = newRule(name, priority, action);
        rule.setHasUsageLimit(true);
        rule.setUsageLimit(5);
        rule.setRemainingUses(remainingUses);

        return promotionRepository.saveAndFlush(rule);
    }

    private static PromotionRule newRule(String name, int priority, ActionStrategy action) {
        PromotionRule rule = new PromotionRule();
        rule.setStorefrontId(STOREFRONT_ID);
        rule.setName(name);
        rule.setPriority(priority);
        rule.setShortCircuit(false);
        rule.setActive(true);
        rule.setConditionTree(new PromoCodeConditionNode("SUMMER", Operator.EQUALS));
        rule.setAction(action);
        return rule;
    }

    private static Product product(String externalId, String sku, Set<Long> categoryIds) {
        return Product.builder()
            .storefrontId(STOREFRONT_ID)
            .externalId(externalId)
            .sku(sku)
            .name("Test Product " + externalId)
            .categoryIds(new HashSet<>(categoryIds))
            .build();
    }

    private static UUID usageId(JsonNode usageNode) {
        return UUID.fromString(usageNode.path("id").asString());
    }

    private PromotionRuleUsageStatus usageStatus(UUID usageId) {
        return usageRepository.findById(usageId).orElseThrow().getStatus();
    }

    private int remainingUses(PromotionRule rule) {
        return promotionRepository.findById(rule.getId()).orElseThrow().getRemainingUses();
    }

    /** Value of the rule's Redis counter; null when the rule has none. */
    private String counter(PromotionRule rule) {
        return redisTemplate.opsForValue().get(COUNTER_KEY_PREFIX + rule.getId());
    }

    private static String rulesCacheKey() {
        return PromotionRuleCacheReader.RULES_CACHE_NAME + "::" + STOREFRONT_ID;
    }

    private List<Long> auditRuleIds(String joinTable, UUID usageId) {
        return jdbcTemplate.queryForList(
            "SELECT rule_id FROM " + joinTable + " WHERE usage_id = ?", Long.class, usageId
        );
    }

    private static PromotionRuleUsageAuditProduct auditProduct(
        List<PromotionRuleUsageAuditProduct> products,
        String productExternalId
    ) {
        return products.stream()
            .filter(product -> productExternalId.equals(product.getProductExternalId()))
            .findFirst()
            .orElseThrow();
    }

    private static List<Long> ruleIds(JsonNode response, String fieldName) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode rule : response.path(fieldName)) {
            ids.add(rule.path("ruleId").asLong());
        }
        return ids;
    }
}
