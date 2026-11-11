package com.promox.core.promotion;

import com.promox.core.category.internal.persistence.Category;
import com.promox.core.category.internal.persistence.CategoryRepository;
import com.promox.core.infrastructure.security.Roles;
import com.promox.core.product.internal.persistence.Product;
import com.promox.core.product.internal.persistence.ProductRepository;
import com.promox.core.promotion.internal.dto.CreatePromotionRuleRequest;
import com.promox.core.promotion.internal.persistence.PromotionRepository;
import com.promox.core.promotion.internal.service.action.OrderDiscountAction;
import com.promox.core.promotion.internal.service.condition.CompositeConditionNode;
import com.promox.core.promotion.internal.service.condition.Operator;
import com.promox.core.promotion.internal.service.condition.SingleProductConditionNode;
import com.promox.core.shared.IntegrationTestBase;
import com.promox.core.testfixtures.security.WithMockJwt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithMockJwt(roles = Roles.PROMOTION_MANAGER)
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

    @Autowired
    private CategoryRepository categoryRepository;

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
    @DisplayName("Category Validation Tests")
    class CategoryValidationTests {

        @Test
        @DisplayName("Should create the rule when every category in its conditions exists in the storefront")
        void createPromotionRule_existingCategories_success() throws Exception {
            saveCategory("CAT-KEYBOARDS", STOREFRONT_ID);

            mockMvc.perform(post("/promotion/")
                    .header("X-Storefront-Id", STOREFRONT_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(ruleWithCategories("CAT-KEYBOARDS")))
                .andExpect(status().isCreated());

            assertThat(promotionRepository.findAllWithProductsBy().getFirst().getCategoryExternalIds())
                .containsExactly("CAT-KEYBOARDS");
        }

        @Test
        @DisplayName("Should return 409 Conflict, listing the missing and existing categories, and save nothing")
        void createPromotionRule_nonExistentCategory_throwsBusinessException() throws Exception {
            saveCategory("CAT-KEYBOARDS", STOREFRONT_ID);

            MvcResult result = mockMvc.perform(post("/promotion/")
                    .header("X-Storefront-Id", STOREFRONT_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(ruleWithCategories("CAT-KEYBOARDS", "CAT-MISSING")))
                .andExpect(status().isConflict())
                .andReturn();

            JsonNode errorNode = objectMapper.readTree(result.getResponse().getContentAsString()).path("errors").get(0);
            assertThat(errorNode.path("code").asString()).isEqualTo("CATEGORY_EXTERNAL_IDS_NOT_FOUND");
            assertThat(extractStringList(errorNode.path("errorContext"), "missingCategoryExternalIds"))
                .containsExactly("CAT-MISSING");
            assertThat(extractStringList(errorNode.path("errorContext"), "existingCategoryExternalIds"))
                .containsExactly("CAT-KEYBOARDS");
            assertThat(promotionRepository.count()).isZero();
        }

        @Test
        @DisplayName("Should reject a category that only exists in another storefront")
        void createPromotionRule_categoryFromAnotherStorefront_throwsBusinessException() throws Exception {
            saveCategory("CAT-FOREIGN", STOREFRONT_ID + 1);

            mockMvc.perform(post("/promotion/")
                    .header("X-Storefront-Id", STOREFRONT_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(ruleWithCategories("CAT-FOREIGN")))
                .andExpect(status().isConflict());

            assertThat(promotionRepository.count()).isZero();
        }

        private void saveCategory(String externalId, Long storefrontId) {
            categoryRepository.save(Category.builder()
                .storefrontId(storefrontId)
                .externalId(externalId)
                .name("Category " + externalId)
                .build());
        }

        private String ruleWithCategories(String... categories) {
            String categoryList = String.join("\", \"", categories);
            return """
            {
              "name": "Keyboard category rule",
              "priority": 10,
              "isShortCircuit": false,
              "isActive": true,
              "hasUsageLimit": false,
              "conditionTree": {
                "type": "PRODUCT_CATEGORY_ANY_MATCH",
                "categories": ["%s"],
                "operator": "EQUALS"
              },
              "action": {"type": "ORDER_FLAT_DISCOUNT", "discountAmount": 10.00}
            }
            """.formatted(categoryList);
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

        @ParameterizedTest(name = "[{index}] {0}")
        @MethodSource("invalidConditionsAndActions")
        @DisplayName("Should explain which condition or action value is invalid instead of reporting malformed JSON")
        void createPromotionRule_invalidConditionOrAction_returnsSpecificError(
            String testName,
            String conditionTree,
            String action,
            String expectedPath,
            String expectedCode,
            String expectedMessage
        ) throws Exception {
            String payload = """
                {
                  "name": "Rule with an invalid value",
                  "priority": 50,
                  "hasUsageLimit": false,
                  "conditionTree": %s,
                  "action": %s
                }
                """.formatted(conditionTree, action);

            mockMvc.perform(post("/promotion/")
                    .header("X-Storefront-Id", STOREFRONT_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].category").value("VALIDATION"))
                .andExpect(jsonPath("$.errors[0].source").value("REQUEST_BODY"))
                .andExpect(jsonPath("$.errors[0].path").value(expectedPath))
                .andExpect(jsonPath("$.errors[0].code").value(expectedCode))
                .andExpect(jsonPath("$.errors[0].message").value(expectedMessage));

            assertThat(promotionRepository.count()).isZero();
        }

        @Test
        @DisplayName("Should list the known types when a condition type is unknown")
        void createPromotionRule_unknownConditionType_listsAllowedTypes() throws Exception {
            String payload = """
                {
                  "name": "Rule with an unknown condition",
                  "priority": 50,
                  "hasUsageLimit": false,
                  "conditionTree": { "type": "NOPE" },
                  "action": { "type": "FREE_SHIPPING_ACTION" }
                }
                """;

            mockMvc.perform(post("/promotion/")
                    .header("X-Storefront-Id", STOREFRONT_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("UNKNOWN_TYPE"))
                .andExpect(jsonPath("$.errors[0].errorContext.allowedTypes", hasItems("PROMO_CODE", "COMPOSITE", "SUBTOTAL")));
        }

        @Test
        @DisplayName("Should list the allowed values when an operator isn't one of them")
        void createPromotionRule_unknownOperator_listsAllowedValues() throws Exception {
            String payload = """
                {
                  "name": "Rule with an unknown operator",
                  "priority": 50,
                  "hasUsageLimit": false,
                  "conditionTree": { "type": "PROMO_CODE", "code": "SUMMER", "operator": "LIKE" },
                  "action": { "type": "FREE_SHIPPING_ACTION" }
                }
                """;

            mockMvc.perform(post("/promotion/")
                    .header("X-Storefront-Id", STOREFRONT_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].path").value("conditionTree.operator"))
                .andExpect(jsonPath("$.errors[0].code").value("TYPE_MISMATCH"))
                .andExpect(jsonPath("$.errors[0].message").value("Invalid value 'LIKE'."))
                .andExpect(jsonPath("$.errors[0].errorContext.allowedValues", hasItems("EQUALS", "NOT_EQUALS", "GREATER_THAN_OR_EQUAL")));
        }

        static Stream<Arguments> invalidConditionsAndActions() {
            String validCondition = """
                { "type": "PROMO_CODE", "code": "SUMMER", "operator": "EQUALS" }""";
            String validAction = """
                { "type": "FREE_SHIPPING_ACTION" }""";

            return Stream.of(
                Arguments.of(
                    "Operator the condition doesn't support",
                    """
                    { "type": "PROMO_CODE", "code": "SUMMER", "operator": "GREATER_THAN_OR_EQUAL" }""",
                    validAction,
                    "conditionTree",
                    "UNSUPPORTED_OPERATOR",
                    "Operator 'GREATER_THAN_OR_EQUAL' is not supported by PromoCodeConditionNode."
                ),
                Arguments.of(
                    "Missing operator in a composite's child",
                    """
                    { "type": "COMPOSITE", "operator": "AND", "children": [ { "type": "PROMO_CODE", "code": "SUMMER" } ] }""",
                    validAction,
                    "conditionTree.children.[0]",
                    "MISSING_REQUIRED_FIELD",
                    "Operator is required."
                ),
                Arguments.of(
                    "Blank promo code",
                    """
                    { "type": "PROMO_CODE", "code": "", "operator": "EQUALS" }""",
                    validAction,
                    "conditionTree",
                    "MISSING_REQUIRED_FIELD",
                    "Promo code value cannot be null or blank."
                ),
                Arguments.of(
                    "Unknown condition type",
                    """
                    { "type": "NOPE" }""",
                    validAction,
                    "conditionTree",
                    "UNKNOWN_TYPE",
                    "Unknown type 'NOPE'."
                ),
                Arguments.of(
                    "Condition without a type",
                    """
                    { "code": "SUMMER", "operator": "EQUALS" }""",
                    validAction,
                    "conditionTree",
                    "MISSING_TYPE",
                    "The 'type' property is required."
                ),
                Arguments.of(
                    "Percentage above 1",
                    validCondition,
                    """
                    { "type": "ORDER_PERCENTAGE_DISCOUNT", "discountPercentage": 1.5 }""",
                    "action",
                    "INVALID_VALUE_RANGE",
                    "Percentage must be from 0.0 to 1"
                ),
                Arguments.of(
                    "Missing discount amount",
                    validCondition,
                    """
                    { "type": "ORDER_FLAT_DISCOUNT" }""",
                    "action",
                    "MISSING_REQUIRED_FIELD",
                    "Discount amount cannot be null"
                ),
                Arguments.of(
                    "Unknown action type",
                    validCondition,
                    """
                    { "type": "NOPE" }""",
                    "action",
                    "UNKNOWN_TYPE",
                    "Unknown type 'NOPE'."
                )
            );
        }
    }

    @Nested
    @DisplayName("Authorization Tests")
    class AuthorizationTests {

        @Test
        @WithMockJwt(roles = {Roles.ADMIN, Roles.EMPLOYEE, Roles.AUDITOR, Roles.INTEGRATION_CLIENT})
        @DisplayName("Should return 403 Forbidden and create nothing when the user is not a promotion manager")
        void createPromotionRule_userIsNotPromotionManager_returnsForbidden() throws Exception {
            String jsonPayload = """
            {
              "name": "Forbidden Rule",
              "priority": 50,
              "isShortCircuit": false,
              "hasUsageLimit": "false",
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
                .andExpect(status().isForbidden());

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
