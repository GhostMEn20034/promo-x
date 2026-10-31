package com.promox.core.promotion;

import com.promox.core.category.event.CategoryUpdatedEvent;
import com.promox.core.category.internal.persistence.Category;
import com.promox.core.category.internal.persistence.CategoryRepository;
import com.promox.core.infrastructure.security.Roles;
import com.promox.core.promotion.event.PromotionRuleUpdatedEvent;
import com.promox.core.promotion.internal.persistence.PromotionRepository;
import com.promox.core.promotion.internal.persistence.PromotionRule;
import com.promox.core.promotion.internal.service.PromotionRuleCacheReader;
import com.promox.core.promotion.internal.service.PromotionService;
import com.promox.core.promotion.internal.service.action.OrderDiscountAction;
import com.promox.core.promotion.internal.service.condition.Operator;
import com.promox.core.promotion.internal.service.condition.SingleProductCategoryConditionNode;
import com.promox.core.promotion.internal.service.eventlistener.PromotionCategoryEventListener;
import com.promox.core.shared.IntegrationTestBase;
import com.promox.core.testfixtures.security.WithMockJwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.modulith.test.EnableScenarios;
import org.springframework.modulith.test.Scenario;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithMockJwt(roles = Roles.EMPLOYEE)
@EnableScenarios
class PromotionCategoryUpdateIntegrationTest extends IntegrationTestBase {
    private static final Long STOREFRONT_ID = 100L;
    private static final Long OTHER_STOREFRONT_ID = 200L;
    private static final String OLD_EXTERNAL_ID = "CAT-KEYBOARDS";
    private static final String NEW_EXTERNAL_ID = "CAT-KEYBOARDS-NEW";

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private PromotionRepository promotionRepository;

    @Autowired
    private PromotionService promotionService;

    @Autowired
    private PromotionRuleCacheReader promotionRuleCacheReader;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private Category category;

    @BeforeEach
    void setUp() {
        category = categoryRepository.save(Category.builder()
            .storefrontId(STOREFRONT_ID)
            .externalId(OLD_EXTERNAL_ID)
            .name("Keyboards")
            .build());
    }

    @Test
    @DisplayName("Should deactivate the rules referencing a category whose external ID changed")
    void updateExternalId_deactivatesReferencingRules(Scenario scenario) {
        PromotionRule first = saveRule(STOREFRONT_ID, true, OLD_EXTERNAL_ID);
        PromotionRule second = saveRule(STOREFRONT_ID, true, OLD_EXTERNAL_ID, "CAT-OTHER");

        scenario.stimulate(this::changeCategoryExternalId)
            .andWaitForStateChange(() -> !isActive(first) && !isActive(second), deactivated -> deactivated)
            .andVerify(deactivated -> assertThat(deactivated).isTrue());
    }

    @Test
    @DisplayName("Should keep other categories' rules, other storefronts' rules and inactive rules untouched")
    void updateExternalId_leavesUnrelatedRulesUntouched(Scenario scenario) {
        PromotionRule referencing = saveRule(STOREFRONT_ID, true, OLD_EXTERNAL_ID);
        PromotionRule otherCategory = saveRule(STOREFRONT_ID, true, "CAT-OTHER");
        PromotionRule otherStorefront = saveRule(OTHER_STOREFRONT_ID, true, OLD_EXTERNAL_ID);
        PromotionRule alreadyInactive = saveRule(STOREFRONT_ID, false, OLD_EXTERNAL_ID);

        scenario.stimulate(this::changeCategoryExternalId)
            .andWaitForStateChange(() -> isActive(referencing), active -> !active)
            .andVerify(_ -> {
                assertThat(isActive(otherCategory)).isTrue();
                assertThat(isActive(otherStorefront)).isTrue();
                assertThat(reload(alreadyInactive).getUpdatedAt()).isCloseTo(
                    alreadyInactive.getUpdatedAt(), within(1, ChronoUnit.MILLIS)
                );
            });
    }

    @Test
    @DisplayName("Should publish an updated event for the deactivated rule, with its new updatedAt")
    void updateExternalId_publishesRuleUpdatedEvent(Scenario scenario) {
        PromotionRule rule = saveRule(STOREFRONT_ID, true, OLD_EXTERNAL_ID);

        scenario.stimulate(this::changeCategoryExternalId)
            .andWaitForEventOfType(PromotionRuleUpdatedEvent.class)
            .matching(event -> event.id().equals(rule.getId()))
            .toArriveAndVerify(event -> {
                assertThat(event.isActive()).isFalse();
                assertThat(event.storefrontId()).isEqualTo(STOREFRONT_ID);
                assertThat(event.categoryExternalIds()).containsExactly(OLD_EXTERNAL_ID);
                assertThat(event.updatedAt()).isAfter(rule.getUpdatedAt());
            });
    }

    @Test
    @DisplayName("Should evict the storefront's cached rules, so deactivated rules stop being evaluated")
    void updateExternalId_evictsCachedRules(Scenario scenario) {
        saveRule(STOREFRONT_ID, true, OLD_EXTERNAL_ID);
        promotionRuleCacheReader.findAllActiveSortedByPriority(STOREFRONT_ID);
        assertThat(rulesCached()).isTrue();

        scenario.stimulate(this::changeCategoryExternalId)
            .andWaitForStateChange(this::rulesCached, cached -> !cached)
            .andVerify(_ -> assertThat(promotionRuleCacheReader.findAllActiveSortedByPriority(STOREFRONT_ID)).isEmpty());
    }

    @Test
    @DisplayName("Should leave rules active when the category's external ID did not change")
    void categoryUpdatedEvent_sameExternalId_keepsRulesActive() {
        PromotionRule rule = saveRule(STOREFRONT_ID, true, OLD_EXTERNAL_ID);

        // Called directly: the proxied listener is asynchronous, so "nothing happened" could not be observed.
        new PromotionCategoryEventListener(promotionService).on(new CategoryUpdatedEvent(
            category.getId(), OLD_EXTERNAL_ID, OLD_EXTERNAL_ID, "Renamed", null, STOREFRONT_ID
        ));

        assertThat(isActive(rule)).isTrue();
    }

    private void changeCategoryExternalId() {
        try {
            mockMvc.perform(put("/category/{categoryId}", category.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"name": "Keyboards", "externalId": "%s"}
                        """.formatted(NEW_EXTERNAL_ID)))
                .andExpect(status().isOk());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private PromotionRule saveRule(Long storefrontId, boolean active, String... categoryExternalIds) {
        PromotionRule rule = new PromotionRule();
        rule.setStorefrontId(storefrontId);
        rule.setName("Keyboard rule");
        rule.setPriority(1);
        rule.setActive(active);
        rule.setConditionTree(new SingleProductCategoryConditionNode(categoryExternalIds[0], Operator.EQUALS));
        rule.setAction(new OrderDiscountAction(BigDecimal.TEN));
        rule.setCategoryExternalIds(new HashSet<>(Set.of(categoryExternalIds)));

        return promotionRepository.saveAndFlush(rule);
    }

    private PromotionRule reload(PromotionRule rule) {
        return promotionRepository.findById(rule.getId()).orElseThrow();
    }

    private boolean isActive(PromotionRule rule) {
        return reload(rule).isActive();
    }

    private boolean rulesCached() {
        return Boolean.TRUE.equals(
            redisTemplate.hasKey(PromotionRuleCacheReader.RULES_CACHE_NAME + "::" + STOREFRONT_ID)
        );
    }
}
