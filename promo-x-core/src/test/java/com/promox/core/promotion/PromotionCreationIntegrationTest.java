package com.promox.core.promotion;

import com.promox.core.product.internal.persistence.Product;
import com.promox.core.product.internal.persistence.ProductRepository;
import com.promox.core.promotion.internal.dto.CreatePromotionRuleRequest;
import com.promox.core.promotion.internal.persistence.PromotionRepository;
import com.promox.core.promotion.internal.service.action.OrderDiscountAction;
import com.promox.core.promotion.internal.service.condition.CompositeConditionNode;
import com.promox.core.promotion.internal.service.condition.Operator;
import com.promox.core.promotion.internal.service.condition.SingleProductConditionNode;
import com.promox.core.shared.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class PromotionCreationIntegrationTest extends IntegrationTestBase {
    private static final Long STOREFRONT_ID = 100L;
    private static final String EXISTING_PRODUCT_1 = "PROD-KB-01";
    private static final String EXISTING_PRODUCT_2 = "PROD-MS-01";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PromotionRepository promotionRepository;

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        promotionRepository.deleteAll();
        productRepository.deleteAll();

        // Seed products with required non-null SKU fields
        productRepository.saveAll(List.of(
            createTestProduct(EXISTING_PRODUCT_1, STOREFRONT_ID),
            createTestProduct(EXISTING_PRODUCT_2, STOREFRONT_ID)
        ));
    }

    @Nested
    @DisplayName("Happy Path Tests")
    class HappyPathTests {

        @Test
        @DisplayName("Should successfully create promotion rule with composite conditions and persist extracted product IDs")
        void createPromotionRule_successful() throws Exception {
            String jsonPayload = """
            {
              "name": "Summer Tech Bundle Discount",
              "priority": 100,
              "isShortCircuit": false,
              "isActive": true,
              "hasUsageLimit": "false",
              "usageLimit": 10,
              "remainingUses": 10,
              "conditionTree": {
                "type": "COMPOSITE",
                "operator": "AND",
                "children": [
                  {
                    "type": "SINGLE_PRODUCT_EXTERNAL_ID_MATCH",
                    "productExternalId": "%s",
                    "operator": "EQUALS"
                  },
                  {
                    "type": "PRODUCT_EXTERNAL_ID_QUANTITY",
                    "productExternalId": "%s",
                    "requiredQuantity": 2,
                    "operator": "GREATER_THAN_OR_EQUAL"
                  }
                ]
              },
              "action": {
                "type": "ORDER_FLAT_DISCOUNT",
                "discountAmount": 25.00
              }
            }
            """.formatted(EXISTING_PRODUCT_1, EXISTING_PRODUCT_2);

            MvcResult result = mockMvc.perform(post("/promotion/")
                    .header("X-Storefront-Id", STOREFRONT_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonPayload))
                .andExpect(status().isCreated())
                .andReturn();

            JsonNode responseJson = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(responseJson.path("name").asString()).isEqualTo("Summer Tech Bundle Discount");
            assertThat(responseJson.path("priority").asInt()).isEqualTo(100);
            assertThat(responseJson.path("isShortCircuit").asBoolean()).isFalse();
            assertThat(responseJson.path("isActive").asBoolean()).isTrue();

            var savedRules = promotionRepository.findAllWithProductsBy();
            assertThat(savedRules).hasSize(1);

            var rule = savedRules.getFirst();
            assertThat(rule.getStorefrontId()).isEqualTo(STOREFRONT_ID);
            assertThat(rule.getName()).isEqualTo("Summer Tech Bundle Discount");
            assertThat(rule.getPriority()).isEqualTo(100);
            assertThat(rule.getProductExternalIds())
                .containsExactlyInAnyOrder(EXISTING_PRODUCT_1, EXISTING_PRODUCT_2);
        }

        @Test
        @DisplayName("Should default isActive to true when omitted in payload")
        void createPromotionRule_defaultIsActive() throws Exception {
            String jsonPayload = """
            {
              "name": "Default Active Rule",
              "priority": 50,
              "isShortCircuit": false,
              "hasUsageLimit": "false",
              "usageLimit": 10,
              "remainingUses": 10,
              "conditionTree": {
                "type": "SINGLE_PRODUCT_EXTERNAL_ID_MATCH",
                "productExternalId": "%s",
                "operator": "EQUALS"
              },
              "action": {
                "type": "ORDER_FLAT_DISCOUNT",
                "discountAmount": 10.00
              }
            }
            """.formatted(EXISTING_PRODUCT_1);

            MvcResult result = mockMvc.perform(post("/promotion/")
                    .header("X-Storefront-Id", STOREFRONT_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonPayload))
                .andExpect(status().isCreated())
                .andReturn();

            JsonNode responseJson = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(responseJson.path("isActive").asBoolean()).isTrue();

            var savedRules = promotionRepository.findAll();
            assertThat(savedRules).hasSize(1);
            assertThat(savedRules.getFirst().isActive()).isTrue();
        }

        @Test
        @DisplayName("Should successfully create promotion rule with usage limit enabled and valid positive limits")
        void createPromotionRule_withUsageLimit_success() throws Exception {
            String jsonPayload = """
            {
              "name": "Limited Flash Sale Discount",
              "priority": 100,
              "isShortCircuit": false,
              "isActive": true,
              "hasUsageLimit": true,
              "usageLimit": 50,
              "remainingUses": 50,
              "conditionTree": {
                "type": "SINGLE_PRODUCT_EXTERNAL_ID_MATCH",
                "productExternalId": "%s",
                "operator": "EQUALS"
              },
              "action": {
                "type": "ORDER_FLAT_DISCOUNT",
                "discountAmount": 20.00
              }
            }
            """.formatted(EXISTING_PRODUCT_1);

            MvcResult result = mockMvc.perform(post("/promotion/")
                            .header("X-Storefront-Id", STOREFRONT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonPayload))
                    .andExpect(status().isCreated())
                    .andReturn();

            JsonNode responseJson = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(responseJson.path("hasUsageLimit").asBoolean()).isTrue();
            assertThat(responseJson.path("usageLimit").asInt()).isEqualTo(50);
            assertThat(responseJson.path("remainingUses").asInt()).isEqualTo(50);

            var savedRules = promotionRepository.findAll();
            assertThat(savedRules).hasSize(1);

            var rule = savedRules.getFirst();
            assertThat(rule.hasUsageLimit()).isTrue();
            assertThat(rule.getUsageLimit()).isEqualTo(50);
            assertThat(rule.getRemainingUses()).isEqualTo(50);
        }

        @Test
        @DisplayName("Should default remainingUses to usageLimit when hasUsageLimit is true and remainingUses is omitted")
        void createPromotionRule_withUsageLimit_defaultRemainingUses() throws Exception {
            String jsonPayload = """
            {
              "name": "Limited Sale Auto Default Uses",
              "priority": 50,
              "isShortCircuit": false,
              "isActive": true,
              "hasUsageLimit": true,
              "usageLimit": 25,
              "conditionTree": {
                "type": "SINGLE_PRODUCT_EXTERNAL_ID_MATCH",
                "productExternalId": "%s",
                "operator": "EQUALS"
              },
              "action": {
                "type": "ORDER_FLAT_DISCOUNT",
                "discountAmount": 10.00
              }
            }
            """.formatted(EXISTING_PRODUCT_1);

            MvcResult result = mockMvc.perform(post("/promotion/")
                            .header("X-Storefront-Id", STOREFRONT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonPayload))
                    .andExpect(status().isCreated())
                    .andReturn();

            JsonNode responseJson = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(responseJson.path("hasUsageLimit").asBoolean()).isTrue();
            assertThat(responseJson.path("usageLimit").asInt()).isEqualTo(25);
            assertThat(responseJson.path("remainingUses").asInt()).isEqualTo(25);

            var savedRules = promotionRepository.findAll();
            assertThat(savedRules).hasSize(1);
            var rule = savedRules.getFirst();
            assertThat(rule.getUsageLimit()).isEqualTo(25);
            assertThat(rule.getRemainingUses()).isEqualTo(25);
        }
    }

    @Nested
    @DisplayName("Business Error Tests")
    class BusinessErrorTests {

        @Test
        @DisplayName("Should fail with 409 Conflict when specified product external IDs do not exist in system")
        void createPromotionRule_nonExistentProducts_throwsBusinessException() throws Exception {
            String nonExistentProduct = "NON-EXISTENT-PROD-999";

            var conditionTree = new CompositeConditionNode(
                CompositeConditionNode.LogicalOperator.AND,
                List.of(
                    new SingleProductConditionNode(EXISTING_PRODUCT_1, Operator.EQUALS),
                    new SingleProductConditionNode(nonExistentProduct, Operator.EQUALS)
                )
            );

            var request = new CreatePromotionRuleRequest(
                "Invalid Product Rule",
                10,
                false,
                true,
                false,
                1,
                1,
                conditionTree,
                new OrderDiscountAction(new BigDecimal("15.00"))
            );

            MvcResult result = mockMvc.perform(post("/promotion/")
                    .header("X-Storefront-Id", STOREFRONT_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andReturn();

            JsonNode responseJson = objectMapper.readTree(result.getResponse().getContentAsString());
            // Navigate into the first error object
            JsonNode errorNode = responseJson.path("errors").get(0);
            assertThat(errorNode.path("code").asString()).isEqualTo("PRODUCT_EXTERNAL_IDS_NOT_FOUND");

            // Extract from errorContext
            JsonNode errorContextNode = errorNode.path("errorContext");
            List<String> missingIds = extractStringList(errorContextNode, "missingProductExternalIds");
            List<String> existingIds = extractStringList(errorContextNode, "existingProductExternalIds");

            assertThat(missingIds).containsExactlyInAnyOrder(nonExistentProduct);
            assertThat(existingIds).containsExactlyInAnyOrder(EXISTING_PRODUCT_1);
            assertThat(promotionRepository.count()).isZero();
        }
    }

    @Nested
    @DisplayName("Validation Error Tests")
    class ValidationErrorTests {

        @Test
        @DisplayName("Should fail validation when mandatory top-level fields are missing or invalid")
        void createPromotionRule_invalidTopLevelFields() throws Exception {
            String invalidPayload = """
                {
                  "name": "",
                  "priority": -5,
                  "isShortCircuit": false,
                  "isActive": true,
                  "hasUsageLimit": true,
                  "usageLimit": 50,
                  "remainingUses": 50,
                  "conditionTree": null,
                  "action": null
                }
                """;

            mockMvc.perform(post("/promotion/")
                    .header("X-Storefront-Id", STOREFRONT_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(invalidPayload))
                .andExpect(status().isBadRequest());

            assertThat(promotionRepository.count()).isZero();
        }

        @Test
        @DisplayName("Should fail validation when nested condition node contains invalid fields")
        void createPromotionRule_invalidNestedConditionNode() throws Exception {
            String invalidConditionPayload = """
                {
                  "name": "Rule with invalid condition",
                  "priority": 50,
                  "isShortCircuit": false,
                  "isActive": true,
                  "hasUsageLimit": true,
                  "usageLimit": 50,
                  "remainingUses": 50,
                  "conditionTree": {
                    "type": "PRODUCT_EXTERNAL_ID_QUANTITY",
                    "productExternalId": "",
                    "requiredQuantity": 0,
                    "operator": null
                  },
                  "action": {
                    "type": "ORDER_FLAT_DISCOUNT",
                    "discountAmount": 10.00
                  }
                }
                """;

            mockMvc.perform(post("/promotion/")
                    .header("X-Storefront-Id", STOREFRONT_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(invalidConditionPayload))
                .andExpect(status().is4xxClientError());

            assertThat(promotionRepository.count()).isZero();
        }

        @Test
        @DisplayName("Should fail validation when composite child node contains invalid fields")
        void createPromotionRule_invalidNestedCompositeChildNode() throws Exception {
            String invalidCompositePayload = """
                {
                  "name": "Rule with invalid composite child",
                  "priority": 50,
                  "isShortCircuit": false,
                  "isActive": true,
                  "hasUsageLimit": true,
                  "usageLimit": 50,
                  "remainingUses": 50,
                  "conditionTree": {
                    "type": "COMPOSITE",
                    "operator": "AND",
                    "children": [
                      {
                        "type": "PRODUCT_EXTERNAL_ID_QUANTITY",
                        "productExternalId": "%s",
                        "requiredQuantity": -1,
                        "operator": "EQUALS"
                      }
                    ]
                  },
                  "action": {
                    "type": "ORDER_FLAT_DISCOUNT",
                    "discountAmount": 10.00
                  }
                }
                """.formatted(EXISTING_PRODUCT_1);

            mockMvc.perform(post("/promotion/")
                    .header("X-Storefront-Id", STOREFRONT_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(invalidCompositePayload))
                .andExpect(status().is4xxClientError());

            assertThat(promotionRepository.count()).isZero();
        }

        @Test
        @DisplayName("Should fail when request is missing mandatory X-Storefront-Id header")
        void createPromotionRule_missingStorefrontHeader() throws Exception {
            var request = new CreatePromotionRuleRequest(
                "Rule without header",
                10,
                false,
                true,
                false,
                1,
                1,
                new SingleProductConditionNode(EXISTING_PRODUCT_1, Operator.EQUALS),
                new OrderDiscountAction(new BigDecimal("5.00"))
            );

            mockMvc.perform(post("/promotion/")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

            assertThat(promotionRepository.count()).isZero();
        }

        @Test
        @DisplayName("Should fail validation when hasUsageLimit is true but usageLimit is zero or negative")
        void createPromotionRule_invalidZeroUsageLimit_whenLimitEnabled() throws Exception {
            String jsonPayload = """
            {
              "name": "Zero Usage Limit Rule",
              "priority": 10,
              "isShortCircuit": false,
              "isActive": true,
              "hasUsageLimit": true,
              "usageLimit": 0,
              "remainingUses": 10,
              "conditionTree": {
                "type": "SINGLE_PRODUCT_EXTERNAL_ID_MATCH",
                "productExternalId": "%s",
                "operator": "EQUALS"
              },
              "action": {
                "type": "ORDER_FLAT_DISCOUNT",
                "discountAmount": 10.00
              }
            }
            """.formatted(EXISTING_PRODUCT_1);

            mockMvc.perform(post("/promotion/")
                            .header("X-Storefront-Id", STOREFRONT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonPayload))
                    .andExpect(status().isBadRequest());

            assertThat(promotionRepository.count()).isZero();
        }

        @Test
        @DisplayName("Should fail validation when hasUsageLimit is true but remainingUses is zero or negative")
        void createPromotionRule_invalidZeroRemainingUses_whenLimitEnabled() throws Exception {
            String jsonPayload = """
            {
              "name": "Zero Remaining Uses Rule",
              "priority": 10,
              "isShortCircuit": false,
              "isActive": true,
              "hasUsageLimit": true,
              "usageLimit": 10,
              "remainingUses": 0,
              "conditionTree": {
                "type": "SINGLE_PRODUCT_EXTERNAL_ID_MATCH",
                "productExternalId": "%s",
                "operator": "EQUALS"
              },
              "action": {
                "type": "ORDER_FLAT_DISCOUNT",
                "discountAmount": 10.00
              }
            }
            """.formatted(EXISTING_PRODUCT_1);

            mockMvc.perform(post("/promotion/")
                            .header("X-Storefront-Id", STOREFRONT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonPayload))
                    .andExpect(status().isBadRequest());

            assertThat(promotionRepository.count()).isZero();
        }

        @Test
        @DisplayName("Should fail validation when mandatory hasUsageLimit field is missing")
        void createPromotionRule_missingHasUsageLimitField() throws Exception {
            String jsonPayload = """
            {
              "name": "Missing Usage Limit Flag",
              "priority": 10,
              "isShortCircuit": false,
              "isActive": true,
              "conditionTree": {
                "type": "SINGLE_PRODUCT_EXTERNAL_ID_MATCH",
                "productExternalId": "%s",
                "operator": "EQUALS"
              },
              "action": {
                "type": "ORDER_FLAT_DISCOUNT",
                "discountAmount": 10.00
              }
            }
            """.formatted(EXISTING_PRODUCT_1);

            mockMvc.perform(post("/promotion/")
                            .header("X-Storefront-Id", STOREFRONT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonPayload))
                    .andExpect(status().isBadRequest());

            assertThat(promotionRepository.count()).isZero();
        }
    }

    private Product createTestProduct(String externalId, Long storefrontId) {
        return Product.builder()
            .storefrontId(storefrontId)
            .sku(externalId + "_PRODUCT")
            .externalId(externalId)
            .name("Test Product " + externalId)
            .build();
    }

    private List<String> extractStringList(JsonNode parentNode, String fieldName) {
        JsonNode arrayNode = parentNode.path(fieldName);
        List<String> list = new ArrayList<>();
        if (arrayNode.isArray()) {
            for (JsonNode element : arrayNode) {
                list.add(element.asString());
            }
        }
        return list;
    }
}
