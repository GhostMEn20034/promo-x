package com.promox.core.promotion;

import com.promox.core.category.internal.persistence.Category;
import com.promox.core.category.internal.persistence.CategoryRepository;
import com.promox.core.infrastructure.security.Roles;
import com.promox.core.product.internal.persistence.Product;
import com.promox.core.product.internal.persistence.ProductRepository;
import com.promox.core.promotion.event.PromotionRuleCreatedEvent;
import com.promox.core.promotion.event.PromotionRuleDeleteEvent;
import com.promox.core.promotion.event.PromotionRuleUpdatedEvent;
import com.promox.core.promotion.event.PromotionRuleUsageUpdatedEvent;
import com.promox.core.promotion.internal.persistence.PromotionRepository;
import com.promox.core.promotion.internal.persistence.PromotionRule;
import com.promox.core.promotion.internal.service.PromotionRuleCacheReader;
import com.promox.core.promotion.internal.service.action.OrderDiscountAction;
import com.promox.core.promotion.internal.service.condition.Operator;
import com.promox.core.promotion.internal.service.condition.SingleProductConditionNode;
import com.promox.core.shared.IntegrationTestBase;
import com.promox.core.testfixtures.security.WithMockJwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.AssertablePublishedEvents;
import org.springframework.modulith.test.EnableScenarios;
import org.springframework.modulith.test.PublishedEventsExtension;
import org.springframework.modulith.test.Scenario;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithMockJwt(roles = Roles.PROMOTION_MANAGER)
@EnableScenarios
class PromotionRuleEventsIntegrationTest extends IntegrationTestBase {
    private static final Long STOREFRONT_ID = 100L;
    private static final Long OTHER_STOREFRONT_ID = 200L;
    private static final String PRODUCT = "PROD-KB-01";
    private static final String ACTION_ONLY_PRODUCT = "PROD-MS-01";

    private static final String RULE_URL = "/promotion/{promotionRuleId}";

    private static final String RULE_PAYLOAD = """
        {
          "name": "New rule",
          "priority": 7,
          "isShortCircuit": true,
          "isActive": true,
          "hasUsageLimit": true,
          "usageLimit": 10,
          "remainingUses": 4,
          "conditionTree": {
            "type": "SINGLE_PRODUCT_EXTERNAL_ID_MATCH",
            "productExternalId": "%s",
            "operator": "EQUALS"
          },
          "action": {"type": "ORDER_FLAT_DISCOUNT", "discountAmount": 10.00}
        }
        """.formatted(PRODUCT);

    private static final String UPDATE_RULE_PAYLOAD = """
        {
          "name": "Renamed rule",
          "priority": 9,
          "isShortCircuit": true,
          "isActive": false,
          "conditionTree": {
            "type": "SINGLE_PRODUCT_EXTERNAL_ID_MATCH",
            "productExternalId": "%s",
            "operator": "EQUALS"
          },
          "action": {"type": "ORDER_FLAT_DISCOUNT", "discountAmount": 25.00}
        }
        """.formatted(PRODUCT);

    /** Mentions categories only in nested conditions, and one product only in the action. */
    private static final String RULE_WITH_CATEGORIES_PAYLOAD = """
        {
          "name": "Categorized rule",
          "priority": 3,
          "isShortCircuit": false,
          "isActive": true,
          "hasUsageLimit": false,
          "conditionTree": {
            "type": "COMPOSITE",
            "operator": "AND",
            "children": [
              {"type": "SINGLE_PRODUCT_EXTERNAL_ID_MATCH", "productExternalId": "%s", "operator": "EQUALS"},
              {"type": "SINGLE_PRODUCT_CATEGORY_MATCH", "category": "CAT-KEYBOARDS", "operator": "EQUALS"},
              {"type": "PRODUCT_CATEGORY_ANY_MATCH", "categories": ["CAT-GAMING", "CAT-OFFICE"], "operator": "EQUALS"}
            ]
          },
          "action": {
            "type": "PRODUCT_EXTERNAL_ID_PERCENTAGE_DISCOUNT_ACTION",
            "targetProductExternalId": "%s",
            "discountPercentage": 0.10
          }
        }
        """.formatted(PRODUCT, ACTION_ONLY_PRODUCT);

    private static final String USAGE_LIMITS_PAYLOAD = """
        {"hasUsageLimit": true, "usageLimit": 50, "remainingUses": 30, "mutateRemainingUses": true}
        """;

    @Autowired
    private PromotionRepository promotionRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private PromotionRuleCacheReader promotionRuleCacheReader;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        productRepository.save(product(PRODUCT, "SKU-KB-01"));
        productRepository.save(product(ACTION_ONLY_PRODUCT, "SKU-MS-01"));
        categoryRepository.save(category("CAT-KEYBOARDS"));
        categoryRepository.save(category("CAT-GAMING"));
        categoryRepository.save(category("CAT-OFFICE"));
    }

    @Test
    @DisplayName("Should publish a created event carrying the saved rule and its timestamps")
    void create_publishesCreatedEvent(Scenario scenario) {
        scenario.stimulate(() -> perform(post("/promotion/")
                .header("X-Storefront-Id", STOREFRONT_ID)
                .content(RULE_PAYLOAD), 201))
            .andWaitForEventOfType(PromotionRuleCreatedEvent.class)
            .toArriveAndVerify(event -> {
                PromotionRule saved = promotionRepository.findWithProductsById(event.id()).orElseThrow();

                assertThat(event.storefrontId()).isEqualTo(STOREFRONT_ID);
                assertThat(event.name()).isEqualTo("New rule");
                assertThat(event.priority()).isEqualTo(7);
                assertThat(event.isShortCircuit()).isTrue();
                assertThat(event.isActive()).isTrue();
                assertThat(event.hasUsageLimit()).isTrue();
                assertThat(event.usageLimit()).isEqualTo(10);
                assertThat(event.remainingUses()).isEqualTo(4);
                assertThat(event.productExternalIds()).containsExactly(PRODUCT);
                assertThat(event.categoryExternalIds()).isEmpty();
                assertThat(event.createdAt()).isCloseTo(saved.getCreatedAt(), within(1, ChronoUnit.MILLIS));
                assertThat(event.updatedAt()).isCloseTo(saved.getUpdatedAt(), within(1, ChronoUnit.MILLIS));
            });
    }

    @Test
    @DisplayName("Should publish an updated event carrying the new updatedAt, not the previous one")
    void update_publishesUpdatedEventWithNewUpdatedAt(Scenario scenario) {
        PromotionRule rule = saveRule(STOREFRONT_ID);

        scenario.stimulate(() -> perform(put(RULE_URL, rule.getId()).content(UPDATE_RULE_PAYLOAD), 200))
            .andWaitForEventOfType(PromotionRuleUpdatedEvent.class)
            .toArriveAndVerify(event -> {
                assertThat(event.id()).isEqualTo(rule.getId());
                assertThat(event.name()).isEqualTo("Renamed rule");
                assertThat(event.priority()).isEqualTo(9);
                assertThat(event.isActive()).isFalse();
                assertThat(event.productExternalIds()).containsExactly(PRODUCT);
                assertThat(event.updatedAt()).isAfter(rule.getUpdatedAt());
                assertThat(event.createdAt()).isCloseTo(rule.getCreatedAt(), within(1, ChronoUnit.MILLIS));
            });
    }

    @Test
    @DisplayName("Should publish a usage updated event carrying the new limits and updatedAt")
    void updateUsageLimits_publishesUsageUpdatedEvent(Scenario scenario) {
        PromotionRule rule = saveRule(STOREFRONT_ID);

        scenario.stimulate(() -> perform(patch(RULE_URL + "/usage-limits", rule.getId()).content(USAGE_LIMITS_PAYLOAD), 200))
            .andWaitForEventOfType(PromotionRuleUsageUpdatedEvent.class)
            .toArriveAndVerify(event -> {
                assertThat(event.id()).isEqualTo(rule.getId());
                assertThat(event.storefrontId()).isEqualTo(STOREFRONT_ID);
                assertThat(event.hasUsageLimit()).isTrue();
                assertThat(event.usageLimit()).isEqualTo(50);
                assertThat(event.remainingUses()).isEqualTo(30);
                assertThat(event.remainingUsesMutated()).isTrue();
                assertThat(event.updatedAt()).isAfter(rule.getUpdatedAt());
            });
    }

    @Test
    @DisplayName("Should delete the rule and publish an event carrying the whole deleted rule")
    void delete_publishesEventWithDeletedRule(Scenario scenario) {
        PromotionRule rule = saveRule(STOREFRONT_ID);

        scenario.stimulate(() -> perform(delete(RULE_URL, rule.getId()), 204))
            .andWaitForEventOfType(PromotionRuleDeleteEvent.class)
            .toArriveAndVerify(event -> {
                assertThat(event.id()).isEqualTo(rule.getId());
                assertThat(event.storefrontId()).isEqualTo(STOREFRONT_ID);
                assertThat(event.name()).isEqualTo("Existing rule");
                assertThat(event.priority()).isEqualTo(1);
                assertThat(event.productExternalIds()).containsExactly(PRODUCT);
                assertThat(event.createdAt()).isCloseTo(rule.getCreatedAt(), within(1, ChronoUnit.MILLIS));
                assertThat(promotionRepository.findById(rule.getId())).isEmpty();
            });
    }

    @Test
    @DisplayName("Should return 404 and publish nothing when the rule to delete does not exist")
    @ExtendWith(PublishedEventsExtension.class)
    void delete_unknownRule_returnsNotFoundWithoutEvent(AssertablePublishedEvents events) {
        perform(delete(RULE_URL, 99999L), 404);

        assertThat(events.ofType(PromotionRuleDeleteEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("Should store and publish product IDs from conditions and actions and category IDs from conditions")
    void create_extractsIdsFromConditionsAndActions(Scenario scenario) {
        scenario.stimulate(() -> perform(post("/promotion/")
                .header("X-Storefront-Id", STOREFRONT_ID)
                .content(RULE_WITH_CATEGORIES_PAYLOAD), 201))
            .andWaitForEventOfType(PromotionRuleCreatedEvent.class)
            .toArriveAndVerify(event -> {
                assertThat(event.productExternalIds()).containsExactlyInAnyOrder(PRODUCT, ACTION_ONLY_PRODUCT);
                assertThat(event.categoryExternalIds())
                    .containsExactlyInAnyOrder("CAT-KEYBOARDS", "CAT-GAMING", "CAT-OFFICE");

                PromotionRule saved = promotionRepository.findWithProductsById(event.id()).orElseThrow();
                assertThat(saved.getProductExternalIds()).containsExactlyInAnyOrder(PRODUCT, ACTION_ONLY_PRODUCT);
                assertThat(saved.getCategoryExternalIds())
                    .containsExactlyInAnyOrder("CAT-KEYBOARDS", "CAT-GAMING", "CAT-OFFICE");
            });
    }

    @Test
    @DisplayName("Should replace the stored category IDs with the ones in the updated conditions")
    void update_replacesCategoryIds(Scenario scenario) {
        PromotionRule rule = saveRuleWithCategory("CAT-OLD");

        scenario.stimulate(() -> perform(put(RULE_URL, rule.getId()).content(RULE_WITH_CATEGORIES_PAYLOAD), 200))
            .andWaitForEventOfType(PromotionRuleUpdatedEvent.class)
            .toArriveAndVerify(event -> {
                assertThat(event.categoryExternalIds())
                    .containsExactlyInAnyOrder("CAT-KEYBOARDS", "CAT-GAMING", "CAT-OFFICE");
                assertThat(promotionRepository.findWithProductsById(rule.getId()).orElseThrow().getCategoryExternalIds())
                    .containsExactlyInAnyOrder("CAT-KEYBOARDS", "CAT-GAMING", "CAT-OFFICE");
            });
    }

    @Test
    @DisplayName("Should clear the stored category IDs when the updated conditions mention no categories")
    void update_withoutCategoryConditions_clearsCategoryIds(Scenario scenario) {
        PromotionRule rule = saveRuleWithCategory("CAT-OLD");

        scenario.stimulate(() -> perform(put(RULE_URL, rule.getId()).content(UPDATE_RULE_PAYLOAD), 200))
            .andWaitForEventOfType(PromotionRuleUpdatedEvent.class)
            .toArriveAndVerify(event -> {
                assertThat(event.categoryExternalIds()).isEmpty();
                assertThat(categoryRowCount(rule)).isZero();
            });
    }

    @Test
    @DisplayName("Should publish the deleted rule's category IDs and remove its stored ones")
    void delete_publishesCategoryIdsAndRemovesRows(Scenario scenario) {
        PromotionRule rule = saveRuleWithCategory("CAT-OLD");

        scenario.stimulate(() -> perform(delete(RULE_URL, rule.getId()), 204))
            .andWaitForEventOfType(PromotionRuleDeleteEvent.class)
            .toArriveAndVerify(event -> {
                assertThat(event.categoryExternalIds()).containsExactly("CAT-OLD");
                assertThat(categoryRowCount(rule)).isZero();
            });
    }

    @Test
    @DisplayName("Should evict the storefront's cached rules when a rule is created")
    void create_evictsStorefrontRules(Scenario scenario) {
        cacheRulesOf(STOREFRONT_ID);

        scenario.stimulate(() -> perform(post("/promotion/")
                .header("X-Storefront-Id", STOREFRONT_ID)
                .content(RULE_PAYLOAD), 201))
            .andWaitForStateChange(() -> rulesCached(STOREFRONT_ID), cached -> !cached)
            .andVerify(_ -> {});
    }

    @Test
    @DisplayName("Should evict the storefront's cached rules when a rule is updated")
    void update_evictsStorefrontRules(Scenario scenario) {
        PromotionRule rule = saveRule(STOREFRONT_ID);
        cacheRulesOf(STOREFRONT_ID);

        scenario.stimulate(() -> perform(put(RULE_URL, rule.getId()).content(UPDATE_RULE_PAYLOAD), 200))
            .andWaitForStateChange(() -> rulesCached(STOREFRONT_ID), cached -> !cached)
            .andVerify(_ -> {});
    }

    @Test
    @DisplayName("Should evict only the rule's storefront when a rule is deleted")
    void delete_evictsOnlyOwnStorefront(Scenario scenario) {
        PromotionRule rule = saveRule(STOREFRONT_ID);
        cacheRulesOf(STOREFRONT_ID);
        cacheRulesOf(OTHER_STOREFRONT_ID);

        scenario.stimulate(() -> perform(delete(RULE_URL, rule.getId()), 204))
            .andWaitForStateChange(() -> rulesCached(STOREFRONT_ID), cached -> !cached)
            .andVerify(_ -> assertThat(rulesCached(OTHER_STOREFRONT_ID)).isTrue());
    }

    private void perform(MockHttpServletRequestBuilder request, int expectedStatus) {
        try {
            mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is(expectedStatus));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private PromotionRule saveRule(Long storefrontId) {
        PromotionRule rule = new PromotionRule();
        rule.setStorefrontId(storefrontId);
        rule.setName("Existing rule");
        rule.setPriority(1);
        rule.setConditionTree(new SingleProductConditionNode(PRODUCT, Operator.EQUALS));
        rule.setAction(new OrderDiscountAction(BigDecimal.TEN));
        rule.setProductExternalIds(new HashSet<>(Set.of(PRODUCT)));

        return promotionRepository.saveAndFlush(rule);
    }

    private PromotionRule saveRuleWithCategory(String categoryExternalId) {
        PromotionRule rule = saveRule(STOREFRONT_ID);
        rule.setCategoryExternalIds(new HashSet<>(Set.of(categoryExternalId)));

        return promotionRepository.saveAndFlush(rule);
    }

    private static Category category(String externalId) {
        return Category.builder()
            .storefrontId(STOREFRONT_ID)
            .externalId(externalId)
            .name("Category " + externalId)
            .build();
    }

    private static Product product(String externalId, String sku) {
        return Product.builder()
            .storefrontId(STOREFRONT_ID)
            .externalId(externalId)
            .sku(sku)
            .name("Product " + externalId)
            .categoryIds(new HashSet<>())
            .build();
    }

    private int categoryRowCount(PromotionRule rule) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM rule_category WHERE rule_id = ?", Integer.class, rule.getId()
        );
    }

    private void cacheRulesOf(Long storefrontId) {
        promotionRuleCacheReader.findAllActiveSortedByPriority(storefrontId);
        assertThat(rulesCached(storefrontId)).isTrue();
    }

    private boolean rulesCached(Long storefrontId) {
        return Boolean.TRUE.equals(
            redisTemplate.hasKey(PromotionRuleCacheReader.RULES_CACHE_NAME + "::" + storefrontId)
        );
    }
}
