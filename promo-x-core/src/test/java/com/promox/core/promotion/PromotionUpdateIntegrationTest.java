package com.promox.core.promotion;

import com.promox.core.infrastructure.security.Roles;
import com.promox.core.product.internal.persistence.Product;
import com.promox.core.product.internal.persistence.ProductRepository;
import com.promox.core.promotion.internal.dto.UpdatePromotionRuleRequest;
import com.promox.core.promotion.internal.dto.UpdateUsageLimitsRequest;
import com.promox.core.promotion.internal.persistence.PromotionRepository;
import com.promox.core.promotion.internal.persistence.PromotionRule;
import com.promox.core.promotion.internal.service.action.OrderDiscountAction;
import com.promox.core.promotion.internal.service.condition.Operator;
import com.promox.core.promotion.internal.service.condition.SingleProductCategoryConditionNode;
import com.promox.core.promotion.internal.service.condition.SingleProductConditionNode;
import com.promox.core.shared.IntegrationTestBase;

import com.promox.core.testfixtures.security.WithMockJwt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WithMockJwt(roles = Roles.PROMOTION_MANAGER)
public class PromotionUpdateIntegrationTest extends IntegrationTestBase {
    private static final String UPDATE_PROMOTION_RULE_URL = "/promotion/{promotionRuleId}";
    private static final String UPDATE_USAGE_LIMITS_URL = "/promotion/{promotionRuleId}/usage-limits";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PromotionRepository promotionRepository;

    @Autowired
    private ProductRepository productRepository;

    private PromotionRule existingRule;

    @BeforeEach
    void setUp() {
        Product oldProduct = new Product();
        oldProduct.setExternalId("PROD-OLD");
        oldProduct.setSku("SKU-PROD-OLD");
        oldProduct.setName("Old Test Product");
        oldProduct.setStorefrontId(100L);
        productRepository.saveAndFlush(oldProduct);

        PromotionRule rule = new PromotionRule();
        rule.setStorefrontId(100L);
        rule.setName("Old Summer Sale");
        rule.setPriority(5);
        rule.setShortCircuit(false);
        rule.setActive(true);
        rule.setConditionTree(new SingleProductConditionNode("PROD-OLD", Operator.EQUALS));
        rule.setAction(new OrderDiscountAction(BigDecimal.TEN));
        rule.setProductExternalIds(Set.of("PROD-OLD"));

        existingRule = promotionRepository.saveAndFlush(rule);
    }

    @Nested
    @DisplayName("Happy Paths")
    class HappyPaths {

        @Test
        @DisplayName("Should successfully update promotion rule using registered actions/conditions and real DB")
        void updatePromotionRule_Success() throws Exception {
            String updatedProductId = "PROD-NEW-99";

            Product newProduct = new Product();
            newProduct.setExternalId(updatedProductId);
            newProduct.setSku("SKU-PROD-NEW-99");
            newProduct.setName("New Test Product");
            newProduct.setStorefrontId(100L);
            productRepository.save(newProduct);

            var conditionTree = new SingleProductConditionNode(updatedProductId, Operator.EQUALS);
            var action = new OrderDiscountAction(new BigDecimal("25.00"));

            UpdatePromotionRuleRequest request = new UpdatePromotionRuleRequest(
                "Updated Black Friday Sale",
                10,
                true,
                true,
                conditionTree,
                action
            );

            mockMvc.perform(put(UPDATE_PROMOTION_RULE_URL, existingRule.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(existingRule.getId()))
                .andExpect(jsonPath("$.storefrontId").value(100))
                .andExpect(jsonPath("$.name").value("Updated Black Friday Sale"))
                .andExpect(jsonPath("$.priority").value(10))
                .andExpect(jsonPath("$.isShortCircuit").value(true))
                .andExpect(jsonPath("$.isActive").value(true)).andExpect(jsonPath("$.conditionTree.node.productExternalId").value(updatedProductId))
                .andExpect(jsonPath("$.action.action.discountAmount").value(25.00))
                .andExpect(jsonPath("$.productExternalIds", hasSize(1)))
                .andExpect(jsonPath("$.productExternalIds[0]").value(updatedProductId));

            PromotionRule reloaded = promotionRepository.findWithProductsById(existingRule.getId()).orElseThrow();
            assertThat(reloaded.getName()).isEqualTo("Updated Black Friday Sale");
            assertThat(reloaded.getPriority()).isEqualTo(10);
            assertThat(reloaded.isShortCircuit()).isTrue();
            assertThat(reloaded.getProductExternalIds()).containsExactly(updatedProductId);
        }

        @Test
        @DisplayName("Should successfully update usage limits when mutateRemainingUses is true")
        void updateUsageLimits_mutateRemainingUsesTrue_success() throws Exception {
            var request = new UpdateUsageLimitsRequest(
                    true,
                    50,
                    30,
                    true
            );

            mockMvc.perform(patch(UPDATE_USAGE_LIMITS_URL, existingRule.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasUsageLimit").value(true))
                    .andExpect(jsonPath("$.usageLimit").value(50))
                    .andExpect(jsonPath("$.remainingUses").value(30));

            PromotionRule reloaded = promotionRepository.findById(existingRule.getId()).orElseThrow();
            assertThat(reloaded.hasUsageLimit()).isTrue();
            assertThat(reloaded.getUsageLimit()).isEqualTo(50);
            assertThat(reloaded.getRemainingUses()).isEqualTo(30);
        }

        @Test
        @DisplayName("Should update usageLimit but leave remainingUses unchanged when mutateRemainingUses is false")
        void updateUsageLimits_mutateRemainingUsesFalse_preservesExistingRemainingUses() throws Exception {
            existingRule.setHasUsageLimit(true);
            existingRule.setUsageLimit(100);
            existingRule.setRemainingUses(85);
            promotionRepository.saveAndFlush(existingRule);

            var request = new UpdateUsageLimitsRequest(
                    true,
                    200,
                    10,
                    false
            );

            mockMvc.perform(patch(UPDATE_USAGE_LIMITS_URL, existingRule.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.usageLimit").value(200))
                    .andExpect(jsonPath("$.remainingUses").value(85));

            PromotionRule reloaded = promotionRepository.findById(existingRule.getId()).orElseThrow();
            assertThat(reloaded.getUsageLimit()).isEqualTo(200);
            assertThat(reloaded.getRemainingUses()).isEqualTo(85);
        }

        @Test
        @DisplayName("Should flatten remainingUses to usageLimit when remainingUses exceeds usageLimit and mutateRemainingUses is true")
        void updateUsageLimits_flattensRemainingUsesToLimit() throws Exception {
            var request = new UpdateUsageLimitsRequest(
                    true,
                    50,
                    120,
                    true
            );

            mockMvc.perform(patch(UPDATE_USAGE_LIMITS_URL, existingRule.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.usageLimit").value(50))
                    .andExpect(jsonPath("$.remainingUses").value(50));

            PromotionRule reloaded = promotionRepository.findById(existingRule.getId()).orElseThrow();
            assertThat(reloaded.getUsageLimit()).isEqualTo(50);
            assertThat(reloaded.getRemainingUses()).isEqualTo(50);
        }

        @Test
        @DisplayName("Should default missing limits to 1 placeholder when usageLimit and remainingUses are null")
        void updateUsageLimits_nullLimits_defaultToPlaceholderOne() throws Exception {
            var request = new UpdateUsageLimitsRequest(
                    false,
                    null,
                    null,
                    true
            );

            mockMvc.perform(patch(UPDATE_USAGE_LIMITS_URL, existingRule.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasUsageLimit").value(false))
                    .andExpect(jsonPath("$.usageLimit").value(1))
                    .andExpect(jsonPath("$.remainingUses").value(1));

            PromotionRule reloaded = promotionRepository.findById(existingRule.getId()).orElseThrow();
            assertThat(reloaded.hasUsageLimit()).isFalse();
            assertThat(reloaded.getUsageLimit()).isEqualTo(1);
            assertThat(reloaded.getRemainingUses()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Business Errors")
    class BusinessErrors {

        @Test
        @DisplayName("Should return 409 CONFLICT and leave the rule unchanged when a category does not exist")
        void updatePromotionRule_CategoriesNotFound() throws Exception {
            UpdatePromotionRuleRequest request = new UpdatePromotionRuleRequest(
                "Updated Black Friday Sale",
                10,
                true,
                true,
                new SingleProductCategoryConditionNode("CAT-MISSING", Operator.EQUALS),
                new OrderDiscountAction(new BigDecimal("25.00"))
            );

            mockMvc.perform(put(UPDATE_PROMOTION_RULE_URL, existingRule.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("CATEGORY_EXTERNAL_IDS_NOT_FOUND"))
                .andExpect(jsonPath("$.errors[0].errorContext.missingCategoryExternalIds[0]").value("CAT-MISSING"));

            PromotionRule reloaded = promotionRepository.findWithProductsById(existingRule.getId()).orElseThrow();
            assertThat(reloaded.getName()).isEqualTo("Old Summer Sale");
            assertThat(reloaded.getCategoryExternalIds()).isEmpty();
        }

        @Test
        @DisplayName("Should return 404 NOT FOUND when promotion rule ID does not exist")
        void updatePromotionRule_NotFound() throws Exception {
            Long nonExistentId = 9999L;
            UpdatePromotionRuleRequest request = new UpdatePromotionRuleRequest(
                "Non-existent Rule",
                1,
                false,
                true,
                new SingleProductConditionNode("PROD-OLD", Operator.EQUALS),
                new OrderDiscountAction(BigDecimal.ONE)
            );

            mockMvc.perform(put(UPDATE_PROMOTION_RULE_URL, nonExistentId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 404 NOT FOUND on usage limits update when promotion rule ID does not exist")
        void updateUsageLimits_NotFound() throws Exception {
            Long nonExistentId = 9999L;
            var request = new UpdateUsageLimitsRequest(true, 10, 10, true);

            mockMvc.perform(patch(UPDATE_USAGE_LIMITS_URL, nonExistentId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 409 business error when product does not exist in productRepository")
        void updatePromotionRule_ProductsNotFound() throws Exception {
            String missingProductId = "PROD-NON-EXISTENT";
            UpdatePromotionRuleRequest request = new UpdatePromotionRuleRequest(
                "Rule With Invalid Product",
                1,
                false,
                true,
                new SingleProductConditionNode(missingProductId, Operator.EQUALS),
                new OrderDiscountAction(BigDecimal.TEN)
            );

            mockMvc.perform(put(UPDATE_PROMOTION_RULE_URL, existingRule.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_EXTERNAL_IDS_NOT_FOUND"))
                .andExpect(jsonPath("$.errors[0].errorContext.missingProductExternalIds").value(missingProductId));
        }
    }

    @Nested
    @DisplayName("Validation Errors")
    class ValidationErrors {

        @Test
        @DisplayName("Should return 400 BAD REQUEST on bean validation failure")
        void updatePromotionRule_InvalidPayload() throws Exception {
            String invalidJson = """
                {
                  "name": "",
                  "priority": -5,
                  "isShortCircuit": false,
                  "isActive": true,
                  "conditionTree": null,
                  "action": null
                }
                """;

            mockMvc.perform(put(UPDATE_PROMOTION_RULE_URL, existingRule.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.example.com/problems/validation-error"))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].category").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("VALIDATION"))))
                .andExpect(jsonPath("$.errors[*].source").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("REQUEST_BODY"))))
                .andExpect(jsonPath("$.errors[*].path", containsInAnyOrder(
                    "name",
                    "priority",
                    "conditionTree",
                    "action"
                )));
        }

        @Test
        @DisplayName("Should return 400 BAD REQUEST when usageLimit is zero or negative")
        void updateUsageLimits_invalidZeroOrNegativeUsageLimit() throws Exception {
            String invalidJson = """
                {
                  "hasUsageLimit": true,
                  "usageLimit": 0,
                  "remainingUses": 10,
                  "mutateRemainingUses": true
                }
                """;

            mockMvc.perform(patch(UPDATE_USAGE_LIMITS_URL, existingRule.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.errors[*].path", containsInAnyOrder("usageLimit")));
        }

        @Test
        @DisplayName("Should return 400 BAD REQUEST when remainingUses is negative")
        void updateUsageLimits_invalidNegativeRemainingUses() throws Exception {
            String invalidJson = """
                {
                  "hasUsageLimit": true,
                  "usageLimit": 10,
                  "remainingUses": -1,
                  "mutateRemainingUses": true
                }
                """;

            mockMvc.perform(patch(UPDATE_USAGE_LIMITS_URL, existingRule.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.errors[*].path", containsInAnyOrder("remainingUses")));
        }

        @Test
        @DisplayName("Should return 400 BAD REQUEST when hasUsageLimit is missing")
        void updateUsageLimits_missingHasUsageLimit() throws Exception {
            String invalidJson = """
                {
                  "usageLimit": 10,
                  "remainingUses": 5,
                  "mutateRemainingUses": true
                }
                """;

            mockMvc.perform(patch(UPDATE_USAGE_LIMITS_URL, existingRule.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.errors[*].path", containsInAnyOrder("hasUsageLimit")));
        }
    }

    @Nested
    @DisplayName("Authorization")
    class Authorization {

        @Test
        @WithMockJwt(roles = {Roles.ADMIN, Roles.EMPLOYEE, Roles.AUDITOR, Roles.INTEGRATION_CLIENT})
        @DisplayName("Should return 403 FORBIDDEN and leave the rule unchanged when the user is not a promotion manager")
        void updatePromotionRule_userIsNotPromotionManager_returnsForbidden() throws Exception {
            UpdatePromotionRuleRequest request = new UpdatePromotionRuleRequest(
                "Updated Black Friday Sale",
                10,
                true,
                true,
                new SingleProductConditionNode("PROD-OLD", Operator.EQUALS),
                new OrderDiscountAction(new BigDecimal("25.00"))
            );

            mockMvc.perform(put(UPDATE_PROMOTION_RULE_URL, existingRule.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

            PromotionRule reloaded = promotionRepository.findById(existingRule.getId()).orElseThrow();
            assertThat(reloaded.getName()).isEqualTo("Old Summer Sale");
            assertThat(reloaded.getPriority()).isEqualTo(5);
            assertThat(reloaded.isShortCircuit()).isFalse();
        }

        @Test
        @WithMockJwt(roles = {Roles.ADMIN, Roles.EMPLOYEE, Roles.AUDITOR, Roles.INTEGRATION_CLIENT})
        @DisplayName("Should return 403 FORBIDDEN and leave usage limits unchanged when the user is not a promotion manager")
        void updateUsageLimits_userIsNotPromotionManager_returnsForbidden() throws Exception {
            var request = new UpdateUsageLimitsRequest(true, 50, 30, true);

            mockMvc.perform(patch(UPDATE_USAGE_LIMITS_URL, existingRule.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());

            PromotionRule reloaded = promotionRepository.findById(existingRule.getId()).orElseThrow();
            assertThat(reloaded.hasUsageLimit()).isFalse();
        }
    }
}
