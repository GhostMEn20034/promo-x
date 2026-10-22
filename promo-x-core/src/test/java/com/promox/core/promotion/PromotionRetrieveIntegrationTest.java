package com.promox.core.promotion;

import com.promox.core.infrastructure.security.Roles;
import com.promox.core.product.internal.persistence.Product;
import com.promox.core.product.internal.persistence.ProductRepository;
import com.promox.core.promotion.internal.persistence.PromotionRepository;
import com.promox.core.promotion.internal.persistence.PromotionRule;
import com.promox.core.promotion.internal.service.action.OrderDiscountAction;
import com.promox.core.promotion.internal.service.condition.Operator;
import com.promox.core.promotion.internal.service.condition.SingleProductConditionNode;
import com.promox.core.shared.IntegrationTestBase;

import com.promox.core.testfixtures.security.WithMockJwt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.util.Set;

@WithMockJwt(roles = {Roles.PROMOTION_MANAGER})
public class PromotionRetrieveIntegrationTest extends IntegrationTestBase {
    private static final String DETAILS_URL_PATH = "/promotion/{promotionRuleId}";
    private static final String LIST_URL_PATH = "/promotion/";

    @Autowired
    private PromotionRepository promotionRepository;

    @Autowired
    private ProductRepository productRepository;

    private PromotionRule existingRule;

    @BeforeEach
    void setUp() {
        Product existingProduct = new Product();
        existingProduct.setExternalId("PROD-101");
        existingProduct.setSku("SKU-101");
        existingProduct.setName("Existing Product");
        existingProduct.setStorefrontId(100L);
        productRepository.saveAndFlush(existingProduct);

        PromotionRule rule = new PromotionRule();
        rule.setStorefrontId(100L);
        rule.setName("Black Friday Special");
        rule.setPriority(1);
        rule.setShortCircuit(true);
        rule.setActive(true);
        rule.setConditionTree(new SingleProductConditionNode("PROD-101", Operator.EQUALS));
        rule.setAction(new OrderDiscountAction(new BigDecimal("15.00")));
        rule.setProductExternalIds(Set.of("PROD-101", "PROD-ABSENT-999"));

        existingRule = promotionRepository.saveAndFlush(rule);
    }

    @Nested
    @DisplayName("Happy Paths")
    class HappyPaths {

        @Test
        @DisplayName("Should successfully return detailed promotion rule with populated existing products")
        void getPromotionRuleById_Success_WithExistingProducts() throws Exception {
            mockMvc.perform(get(DETAILS_URL_PATH, existingRule.getId())
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(existingRule.getId()))
                .andExpect(jsonPath("$.storefrontId").value(100))
                .andExpect(jsonPath("$.name").value("Black Friday Special"))
                .andExpect(jsonPath("$.priority").value(1))
                .andExpect(jsonPath("$.isShortCircuit").value(true))
                .andExpect(jsonPath("$.isActive").value(true))
                .andExpect(jsonPath("$.products", hasSize(1)))
                .andExpect(jsonPath("$.products[0].externalId").value("PROD-101"))
                .andExpect(jsonPath("$.products[0].sku").value("SKU-101"))
                .andExpect(jsonPath("$.products[0].name").value("Existing Product"));
        }

        @Test
        @DisplayName("Should return 200 OK with empty product list when all product external IDs are absent from DB")
        void getPromotionRuleById_Success_WhenAllProductsAbsent() throws Exception {
            PromotionRule ruleWithAbsentProducts = new PromotionRule();
            ruleWithAbsentProducts.setStorefrontId(100L);
            ruleWithAbsentProducts.setName("Ghost Promotion");
            ruleWithAbsentProducts.setPriority(2);
            ruleWithAbsentProducts.setShortCircuit(false);
            ruleWithAbsentProducts.setActive(true);
            ruleWithAbsentProducts.setConditionTree(new SingleProductConditionNode("GHOST-1", Operator.EQUALS));
            ruleWithAbsentProducts.setAction(new OrderDiscountAction(BigDecimal.TEN));
            ruleWithAbsentProducts.setProductExternalIds(Set.of("GHOST-1", "GHOST-2"));

            PromotionRule savedRule = promotionRepository.saveAndFlush(ruleWithAbsentProducts);

            mockMvc.perform(get(DETAILS_URL_PATH, savedRule.getId())
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(savedRule.getId()))
                .andExpect(jsonPath("$.name").value("Ghost Promotion"))
                .andExpect(jsonPath("$.products").isArray())
                .andExpect(jsonPath("$.products", empty()));
        }

        @Test
        @DisplayName("Should successfully return paginated promotion summaries for valid storefront")
        void getPromotionRules_Success() throws Exception {
            mockMvc.perform(get(LIST_URL_PATH)
                    .header("X-Storefront-Id", 100L)
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(existingRule.getId()))
                .andExpect(jsonPath("$.content[0].storefrontId").value(100))
                .andExpect(jsonPath("$.content[0].name").value("Black Friday Special"))
                .andExpect(jsonPath("$.content[0].priority").value(1))
                .andExpect(jsonPath("$.content[0].isShortCircuit").value(true))
                .andExpect(jsonPath("$.content[0].isActive").value(true))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
        }

        @Test
        @DisplayName("Should return empty page when storefront has no promotions")
        void getPromotionRules_Success_EmptyPageForDifferentStorefront() throws Exception {
            Long nonExistentStorefrontId = 999L;

            mockMvc.perform(get(LIST_URL_PATH)
                    .header("X-Storefront-Id", nonExistentStorefrontId)
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", empty()))
                .andExpect(jsonPath("$.totalElements").value(0));
        }
    }

    @Nested
    @DisplayName("Error Paths")
    class ErrorPaths {

        @Test
        @DisplayName("Should return 404 NOT FOUND when promotion rule ID does not exist")
        void getPromotionRuleById_NotFound() throws Exception {
            Long nonExistentId = 99999L;

            mockMvc.perform(get(DETAILS_URL_PATH, nonExistentId)
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 400 BAD REQUEST when X-Storefront-Id header is missing")
        void getPromotionRules_MissingStorefrontHeader() throws Exception {
            mockMvc.perform(get(LIST_URL_PATH)
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Authorization")
    class Authorization {

        @Test
        @WithMockJwt(roles = Roles.PROMOTION_MANAGER)
        @DisplayName("Should return 200 OK with the promotion rule when the user is a promotion manager")
        void getPromotionRuleById_promotionManager_returnsOk() throws Exception {
            mockMvc.perform(get(DETAILS_URL_PATH, existingRule.getId())
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(existingRule.getId()));
        }

        @Test
        @WithMockJwt(roles = Roles.AUDITOR)
        @DisplayName("Should return 200 OK with the promotion rule when the user is an auditor")
        void getPromotionRuleById_auditor_returnsOk() throws Exception {
            mockMvc.perform(get(DETAILS_URL_PATH, existingRule.getId())
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(existingRule.getId()));
        }

        @Test
        @WithMockJwt(roles = Roles.PROMOTION_MANAGER)
        @DisplayName("Should return 200 OK with promotion summaries when the user is a promotion manager")
        void getPromotionRules_promotionManager_returnsOk() throws Exception {
            mockMvc.perform(get(LIST_URL_PATH)
                    .header("X-Storefront-Id", 100L)
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(existingRule.getId()));
        }

        @Test
        @WithMockJwt(roles = Roles.AUDITOR)
        @DisplayName("Should return 200 OK with promotion summaries when the user is an auditor")
        void getPromotionRules_auditor_returnsOk() throws Exception {
            mockMvc.perform(get(LIST_URL_PATH)
                    .header("X-Storefront-Id", 100L)
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(existingRule.getId()));
        }
    }
}
