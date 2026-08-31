package com.promox.core.promotion;

import com.promox.core.product.internal.persistence.Product;
import com.promox.core.product.internal.persistence.ProductRepository;
import com.promox.core.promotion.internal.dto.UpdatePromotionRuleRequest;
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
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


public class PromotionUpdateIntegrationTest extends IntegrationTestBase {
    private static final String BASE_URL = "/promotion/{promotionRuleId}";

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

            mockMvc.perform(put(BASE_URL, existingRule.getId())
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
    }

    @Nested
    @DisplayName("Business Errors")
    class BusinessErrors {

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

            mockMvc.perform(put(BASE_URL, nonExistentId)
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

            mockMvc.perform(put(BASE_URL, existingRule.getId())
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

            mockMvc.perform(put(BASE_URL, existingRule.getId())
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
    }
}
