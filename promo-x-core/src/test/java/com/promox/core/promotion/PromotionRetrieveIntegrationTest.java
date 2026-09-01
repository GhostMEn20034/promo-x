package com.promox.core.promotion;

import com.promox.core.product.internal.persistence.Product;
import com.promox.core.product.internal.persistence.ProductRepository;
import com.promox.core.promotion.internal.persistence.PromotionRepository;
import com.promox.core.promotion.internal.persistence.PromotionRule;
import com.promox.core.promotion.internal.service.action.OrderDiscountAction;
import com.promox.core.promotion.internal.service.condition.Operator;
import com.promox.core.promotion.internal.service.condition.SingleProductConditionNode;
import com.promox.core.shared.IntegrationTestBase;

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


public class PromotionRetrieveIntegrationTest extends IntegrationTestBase {
    private static final String DETAILS_URL_PATH = "/promotion/{promotionRuleId}";

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
    }
}
