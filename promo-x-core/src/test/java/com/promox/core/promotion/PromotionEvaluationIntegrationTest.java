package com.promox.core.promotion;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import com.promox.core.category.internal.persistence.Category;
import com.promox.core.category.internal.persistence.CategoryRepository;
import com.promox.core.infrastructure.security.Roles;
import com.promox.core.product.internal.persistence.Product;
import com.promox.core.product.internal.persistence.ProductRepository;
import com.promox.core.promotion.event.PromotionRulesAppliedEvent;
import com.promox.core.promotion.internal.persistence.PromotionRepository;
import com.promox.core.promotion.internal.persistence.PromotionRule;
import com.promox.core.promotion.internal.service.PromotionRuleCacheReader;
import com.promox.core.promotion.internal.service.action.ActionStrategy;
import com.promox.core.promotion.internal.service.action.FreeShippingAction;
import com.promox.core.promotion.internal.service.action.OrderDiscountAction;
import com.promox.core.promotion.internal.service.action.ProductPercentageDiscountAction;
import com.promox.core.promotion.internal.service.condition.ConditionNode;
import com.promox.core.promotion.internal.service.condition.Operator;
import com.promox.core.promotion.internal.service.condition.PromoCodeConditionNode;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.integration.redis.util.RedisLockRegistry;
import org.springframework.modulith.test.EnableScenarios;
import org.springframework.modulith.test.Scenario;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.securityContext;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithMockJwt(roles = Roles.PROMOTION_MANAGER)
@EnableScenarios
public class PromotionEvaluationIntegrationTest extends IntegrationTestBase {
    private static final String EVALUATE_URL_PATH = "/promotion/evaluate";
    private static final Long STOREFRONT_ID = 100L;
    private static final Long OTHER_STOREFRONT_ID = 200L;

    private static final String KEYBOARD = "PROD-KB-01";
    private static final String MOUSE = "PROD-MS-01";

    private static final String NO_PROMO_CODES = "[]";
    private static final String SUMMER_PROMO_CODE = "[\"SUMMER\"]";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PromotionRepository promotionRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private RedisLockRegistry cacheLockRegistry;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        productRepository.saveAll(List.of(
            createTestProduct(KEYBOARD, "SKU-KB-01", STOREFRONT_ID, Set.of()),
            createTestProduct(MOUSE, "SKU-MS-01", STOREFRONT_ID, Set.of())
        ));
    }

    @Nested
    @DisplayName("Happy Path Tests")
    class HappyPathTests {

        @Test
        @DisplayName("Should return the order unchanged when the storefront has no promotion rules")
        void evaluate_noRules_returnsUnchangedOrder() throws Exception {
            JsonNode response = evaluateOk(cartPayload(SUMMER_PROMO_CODE));

            assertThat(response.path("sessionId").asString()).isEqualTo("sess-1");
            assertThat(response.path("customerId").asString()).isEqualTo("cust-1");
            assertThat(extractStringList(response, "promoCodes")).containsExactly("SUMMER");

            assertThat(decimal(response, "oldTotal")).isEqualByComparingTo("265.00");
            assertThat(decimal(response, "newTotal")).isEqualByComparingTo("265.00");
            assertThat(decimal(response, "totalDiscount")).isEqualByComparingTo("0.00");

            assertThat(decimal(response, "itemsSubtotal")).isEqualByComparingTo("250.00");
            assertThat(decimal(response, "itemsDiscount")).isEqualByComparingTo("0.00");
            assertThat(decimal(response, "itemsTotal")).isEqualByComparingTo("250.00");

            assertThat(decimal(response, "oldShippingCost")).isEqualByComparingTo("15.00");
            assertThat(decimal(response, "shippingDiscount")).isEqualByComparingTo("0.00");
            assertThat(decimal(response, "newShippingCost")).isEqualByComparingTo("15.00");

            assertThat(response.path("items")).hasSize(2);
            JsonNode keyboard = line(response, KEYBOARD);
            assertThat(keyboard.path("productSku").asString()).isEqualTo("SKU-KB-01");
            assertThat(keyboard.path("quantity").asInt()).isEqualTo(2);
            assertThat(decimal(keyboard, "unitPrice")).isEqualByComparingTo("100.00");
            assertThat(decimal(keyboard, "priceBeforeDiscount")).isEqualByComparingTo("200.00");
            assertThat(decimal(keyboard, "discountAmount")).isEqualByComparingTo("0.00");
            assertThat(decimal(keyboard, "priceAfterDiscount")).isEqualByComparingTo("200.00");

            assertThat(response.path("matchedRules")).isEmpty();
            assertThat(response.path("appliedRules")).isEmpty();
        }

        @Test
        @DisplayName("Should apply a product percentage discount only to the targeted line item")
        void evaluate_productPercentageDiscount_reducesOnlyTargetLine() throws Exception {
            PromotionRule rule = saveRule(
                STOREFRONT_ID, "20% off keyboards", 100, false, true,
                new PromoCodeConditionNode("SUMMER", Operator.EQUALS),
                new ProductPercentageDiscountAction(KEYBOARD, new BigDecimal("0.20"))
            );

            JsonNode response = evaluateOk(cartPayload(SUMMER_PROMO_CODE));

            JsonNode keyboard = line(response, KEYBOARD);
            assertThat(decimal(keyboard, "priceBeforeDiscount")).isEqualByComparingTo("200.00");
            assertThat(decimal(keyboard, "discountAmount")).isEqualByComparingTo("40.00");
            assertThat(decimal(keyboard, "priceAfterDiscount")).isEqualByComparingTo("160.00");

            JsonNode mouse = line(response, MOUSE);
            assertThat(decimal(mouse, "discountAmount")).isEqualByComparingTo("0.00");
            assertThat(decimal(mouse, "priceAfterDiscount")).isEqualByComparingTo("50.00");

            assertThat(decimal(response, "itemsSubtotal")).isEqualByComparingTo("250.00");
            assertThat(decimal(response, "itemsDiscount")).isEqualByComparingTo("40.00");
            assertThat(decimal(response, "itemsTotal")).isEqualByComparingTo("210.00");
            assertThat(decimal(response, "totalDiscount")).isEqualByComparingTo("40.00");
            assertThat(decimal(response, "oldTotal")).isEqualByComparingTo("265.00");
            assertThat(decimal(response, "newTotal")).isEqualByComparingTo("225.00");

            assertThat(ruleIds(response, "matchedRules")).containsExactly(rule.getId());
            assertThat(ruleIds(response, "appliedRules")).containsExactly(rule.getId());
            assertThat(response.path("appliedRules").get(0).path("ruleName").asString())
                .isEqualTo("20% off keyboards");
        }

        @Test
        @DisplayName("Should distribute an order flat discount across line items proportionally to their subtotal")
        void evaluate_orderFlatDiscount_distributesProRata() throws Exception {
            saveRule(
                STOREFRONT_ID, "25 off order", 100, false, true,
                new PromoCodeConditionNode("SUMMER", Operator.EQUALS),
                new OrderDiscountAction(new BigDecimal("25.00"))
            );

            JsonNode response = evaluateOk(cartPayload(SUMMER_PROMO_CODE));

            assertThat(decimal(line(response, KEYBOARD), "discountAmount")).isEqualByComparingTo("20.00");
            assertThat(decimal(line(response, MOUSE), "discountAmount")).isEqualByComparingTo("5.00");
            assertThat(decimal(response, "itemsDiscount")).isEqualByComparingTo("25.00");
            assertThat(decimal(response, "itemsTotal")).isEqualByComparingTo("225.00");
            assertThat(decimal(response, "newTotal")).isEqualByComparingTo("240.00");
        }

        @Test
        @DisplayName("Should waive the shipping cost when a free shipping rule applies")
        void evaluate_freeShipping_zeroesShipping() throws Exception {
            saveRule(
                STOREFRONT_ID, "Free shipping", 100, false, true,
                new PromoCodeConditionNode("SUMMER", Operator.EQUALS),
                new FreeShippingAction()
            );

            JsonNode response = evaluateOk(cartPayload(SUMMER_PROMO_CODE));

            assertThat(decimal(response, "oldShippingCost")).isEqualByComparingTo("15.00");
            assertThat(decimal(response, "shippingDiscount")).isEqualByComparingTo("15.00");
            assertThat(decimal(response, "newShippingCost")).isEqualByComparingTo("0.00");
            assertThat(decimal(response, "itemsDiscount")).isEqualByComparingTo("0.00");
            assertThat(decimal(response, "totalDiscount")).isEqualByComparingTo("15.00");
            assertThat(decimal(response, "newTotal")).isEqualByComparingTo("250.00");
        }

        @Test
        @DisplayName("Should execute rules from highest to lowest priority regardless of insertion order")
        void evaluate_multipleRules_executedInPriorityOrder() throws Exception {
            PromotionRule low = saveRule(
                STOREFRONT_ID, "Low: free shipping", 10, false, true,
                new PromoCodeConditionNode("SUMMER", Operator.EQUALS),
                new FreeShippingAction()
            );
            PromotionRule high = saveRule(
                STOREFRONT_ID, "High: 10% off keyboards", 100, false, true,
                new PromoCodeConditionNode("SUMMER", Operator.EQUALS),
                new ProductPercentageDiscountAction(KEYBOARD, new BigDecimal("0.10"))
            );
            PromotionRule mid = saveRule(
                STOREFRONT_ID, "Mid: 50% off mouse", 50, false, true,
                new PromoCodeConditionNode("SUMMER", Operator.EQUALS),
                new ProductPercentageDiscountAction(MOUSE, new BigDecimal("0.50"))
            );

            JsonNode response = evaluateOk(cartPayload(SUMMER_PROMO_CODE));

            assertThat(ruleIds(response, "matchedRules")).containsExactly(high.getId(), mid.getId(), low.getId());
            assertThat(ruleIds(response, "appliedRules")).containsExactly(high.getId(), mid.getId(), low.getId());

            assertThat(decimal(response, "itemsDiscount")).isEqualByComparingTo("45.00");
            assertThat(decimal(response, "shippingDiscount")).isEqualByComparingTo("15.00");
            assertThat(decimal(response, "totalDiscount")).isEqualByComparingTo("60.00");
            assertThat(decimal(response, "newTotal")).isEqualByComparingTo("205.00");
        }

        @Test
        @DisplayName("Should stop evaluating lower priority rules after a short-circuit rule applies")
        void evaluate_shortCircuitRule_stopsLowerPriorityRules() throws Exception {
            PromotionRule high = saveRule(
                STOREFRONT_ID, "High: 10% off keyboards", 100, true, true,
                new PromoCodeConditionNode("SUMMER", Operator.EQUALS),
                new ProductPercentageDiscountAction(KEYBOARD, new BigDecimal("0.10"))
            );
            saveRule(
                STOREFRONT_ID, "Low: free shipping", 10, false, true,
                new PromoCodeConditionNode("SUMMER", Operator.EQUALS),
                new FreeShippingAction()
            );

            JsonNode response = evaluateOk(cartPayload(SUMMER_PROMO_CODE));

            assertThat(ruleIds(response, "matchedRules")).containsExactly(high.getId());
            assertThat(ruleIds(response, "appliedRules")).containsExactly(high.getId());
            assertThat(decimal(response, "shippingDiscount")).isEqualByComparingTo("0.00");
            assertThat(decimal(response, "newShippingCost")).isEqualByComparingTo("15.00");
            assertThat(decimal(response, "newTotal")).isEqualByComparingTo("245.00");
        }

        @Test
        @DisplayName("Should report no matched or applied rules when the rule condition is not met")
        void evaluate_ruleConditionNotMet_reportsNoMatch() throws Exception {
            saveRule(
                STOREFRONT_ID, "Summer only", 100, false, true,
                new PromoCodeConditionNode("SUMMER", Operator.EQUALS),
                new FreeShippingAction()
            );

            JsonNode response = evaluateOk(cartPayload(NO_PROMO_CODES));

            assertThat(response.path("matchedRules")).isEmpty();
            assertThat(response.path("appliedRules")).isEmpty();
            assertThat(decimal(response, "newTotal")).isEqualByComparingTo("265.00");
        }

        @Test
        @DisplayName("Should ignore inactive rules and rules that belong to another storefront")
        void evaluate_inactiveAndOtherStorefrontRules_areIgnored() throws Exception {
            saveRule(
                STOREFRONT_ID, "Inactive rule", 100, false, false,
                new PromoCodeConditionNode("SUMMER", Operator.EQUALS),
                new ProductPercentageDiscountAction(KEYBOARD, new BigDecimal("0.20"))
            );
            saveRule(
                OTHER_STOREFRONT_ID, "Other storefront rule", 100, false, true,
                new PromoCodeConditionNode("SUMMER", Operator.EQUALS),
                new ProductPercentageDiscountAction(KEYBOARD, new BigDecimal("0.20"))
            );

            JsonNode response = evaluateOk(cartPayload(SUMMER_PROMO_CODE));

            assertThat(response.path("matchedRules")).isEmpty();
            assertThat(response.path("appliedRules")).isEmpty();
            assertThat(decimal(response, "totalDiscount")).isEqualByComparingTo("0.00");
            assertThat(decimal(response, "newTotal")).isEqualByComparingTo("265.00");
        }

        @Test
        @DisplayName("Should return a separate result line when the same product appears on multiple order lines")
        void evaluate_sameProductOnMultipleLines_returnsLinePerItem() throws Exception {
            String payload = requestJson(
                "\"promoCodes\": " + NO_PROMO_CODES,
                "\"items\": [" + item(KEYBOARD, 1, "100.00") + "," + item(KEYBOARD, 3, "90.00") + "]",
                "\"shippingCost\": 0.00"
            );

            JsonNode response = evaluateOk(payload);

            assertThat(response.path("items")).hasSize(2);
            assertThat(decimal(response, "itemsSubtotal")).isEqualByComparingTo("370.00");
            assertThat(decimal(response, "newTotal")).isEqualByComparingTo("370.00");
        }

        @Test
        @DisplayName("Should populate line item categories from the product catalog")
        void evaluate_productWithCategories_populatesLineCategories() throws Exception {
            Category category = categoryRepository.save(
                Category.builder()
                    .externalId("CAT-KEYBOARDS")
                    .name("Keyboards")
                    .storefrontId(STOREFRONT_ID)
                    .build()
            );
            productRepository.save(
                createTestProduct("PROD-KB-CAT", "SKU-KB-CAT", STOREFRONT_ID, Set.of(category.getId()))
            );

            String payload = requestJson(
                "\"promoCodes\": " + NO_PROMO_CODES,
                "\"items\": [" + item("PROD-KB-CAT", 1, "80.00") + "," + item(MOUSE, 1, "20.00") + "]",
                "\"shippingCost\": 0.00"
            );

            JsonNode response = evaluateOk(payload);

            assertThat(extractStringList(line(response, "PROD-KB-CAT"), "categories")).containsExactly("CAT-KEYBOARDS");
            assertThat(extractStringList(line(response, MOUSE), "categories")).isEmpty();
        }

        @Test
        @DisplayName("Should decrement remaining uses only for applied rules that have a usage limit")
        void evaluate_appliedRuleWithUsageLimit_reducesRemainingUses(Scenario scenario) {
            PromotionRule limited = saveLimitedRule("Limited keyboards", "SUMMER", 5);
            PromotionRule limitedNotMatched = saveLimitedRule("Limited other code", "WINTER", 5);
            PromotionRule unlimited = saveRule(
                STOREFRONT_ID, "Unlimited free shipping", 10, false, true,
                new PromoCodeConditionNode("SUMMER", Operator.EQUALS),
                new FreeShippingAction()
            );

            scenario.stimulate(() -> evaluateOkUnchecked(cartPayload(SUMMER_PROMO_CODE)))
                .andWaitForStateChange(() -> remainingUses(limited), remaining -> remaining == 4)
                .andVerify((_, response) -> {
                    assertThat(ruleIds(response, "appliedRules")).containsExactly(limited.getId(), unlimited.getId());
                    assertThat(remainingUses(limitedNotMatched)).isEqualTo(5);
                    assertThat(promotionRepository.findById(unlimited.getId()).orElseThrow().hasUsageLimit()).isFalse();
                    assertThat(remainingUses(unlimited)).isZero();
                });
        }

        @Test
        @DisplayName("Should publish PromotionRulesAppliedEvent with the applied rule IDs")
        void evaluate_appliedRules_publishesRulesAppliedEvent(Scenario scenario) {
            PromotionRule keyboards = saveRule(
                STOREFRONT_ID, "10% off keyboards", 100, false, true,
                new PromoCodeConditionNode("SUMMER", Operator.EQUALS),
                new ProductPercentageDiscountAction(KEYBOARD, new BigDecimal("0.10"))
            );
            PromotionRule shipping = saveRule(
                STOREFRONT_ID, "Free shipping", 10, false, true,
                new PromoCodeConditionNode("SUMMER", Operator.EQUALS),
                new FreeShippingAction()
            );

            scenario.stimulate(() -> evaluateOkUnchecked(cartPayload(SUMMER_PROMO_CODE)))
                .andWaitForEventOfType(PromotionRulesAppliedEvent.class)
                .matchingMappedValue(PromotionRulesAppliedEvent::storefrontId, STOREFRONT_ID)
                .toArriveAndVerify(event ->
                    assertThat(event.appliedRuleIds()).containsExactly(keyboards.getId(), shipping.getId())
                );
        }
    }

    @Nested
    @DisplayName("Business Error Tests")
    class BusinessErrorTests {

        @Test
        @DisplayName("Should fail with 409 Conflict when some product external IDs of the order do not exist")
        void evaluate_nonExistentProducts_throwsBusinessException() throws Exception {
            String missingProduct = "NON-EXISTENT-PROD-999";
            String payload = requestJson(
                "\"promoCodes\": " + NO_PROMO_CODES,
                "\"items\": [" + item(KEYBOARD, 1, "100.00") + "," + item(missingProduct, 1, "10.00") + "]",
                "\"shippingCost\": 0.00"
            );

            MvcResult result = evaluate(STOREFRONT_ID, payload)
                .andExpect(status().isConflict())
                .andReturn();

            JsonNode errorNode = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("errors").get(0);
            assertThat(errorNode.path("code").asString()).isEqualTo("PRODUCT_EXTERNAL_IDS_NOT_FOUND");
            assertThat(errorNode.path("path").asString()).isEqualTo("items");

            JsonNode errorContext = errorNode.path("errorContext");
            assertThat(extractStringList(errorContext, "missingProductExternalIds")).containsExactly(missingProduct);
            assertThat(extractStringList(errorContext, "existingProductExternalIds")).containsExactly(KEYBOARD);
        }

        @Test
        @DisplayName("Should fail with 409 Conflict when a product exists only in another storefront")
        void evaluate_productFromAnotherStorefront_throwsBusinessException() throws Exception {
            productRepository.save(
                createTestProduct("PROD-FOREIGN-01", "SKU-FOREIGN-01", OTHER_STOREFRONT_ID, Set.of())
            );
            String payload = requestJson(
                "\"promoCodes\": " + NO_PROMO_CODES,
                "\"items\": [" + item("PROD-FOREIGN-01", 1, "10.00") + "]",
                "\"shippingCost\": 0.00"
            );

            MvcResult result = evaluate(STOREFRONT_ID, payload)
                .andExpect(status().isConflict())
                .andReturn();

            JsonNode errorContext = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("errors").get(0).path("errorContext");
            assertThat(extractStringList(errorContext, "missingProductExternalIds")).containsExactly("PROD-FOREIGN-01");
            assertThat(extractStringList(errorContext, "existingProductExternalIds")).isEmpty();
        }

        @Test
        @DisplayName("Should not consume rule usage limits when the evaluation fails")
        void evaluate_failedEvaluation_doesNotConsumeUsageLimits() throws Exception {
            PromotionRule limited = saveLimitedRule("Limited keyboards", "SUMMER", 5);
            String payload = requestJson(
                "\"promoCodes\": " + SUMMER_PROMO_CODE,
                "\"items\": [" + item("NON-EXISTENT-PROD-999", 1, "10.00") + "]",
                "\"shippingCost\": 0.00"
            );

            evaluate(STOREFRONT_ID, payload).andExpect(status().isConflict());

            assertThat(remainingUses(limited)).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("Validation Error Tests")
    class ValidationErrorTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidPayloads")
        @DisplayName("Should fail with 400 Bad Request when the request body is invalid")
        void evaluate_invalidBody_returnsBadRequest(String scenario, String payload) throws Exception {
            evaluate(STOREFRONT_ID, payload).andExpect(status().isBadRequest());
        }

        static Stream<Arguments> invalidPayloads() {
            String promoCodes = "\"promoCodes\": " + NO_PROMO_CODES;
            String shipping = "\"shippingCost\": 15.00";
            String validItems = "\"items\": [" + item(KEYBOARD, 1, "10.00") + "]";
            String tooManyItems = "\"items\": [" + IntStream.rangeClosed(1, 129)
                .mapToObj(i -> item(KEYBOARD, 1, "1.00"))
                .collect(java.util.stream.Collectors.joining(",")) + "]";

            return Stream.of(
                Arguments.of("items is missing", requestJson(promoCodes, shipping)),
                Arguments.of("items list is empty", requestJson(promoCodes, "\"items\": []", shipping)),
                Arguments.of("items list exceeds 128 elements", requestJson(promoCodes, tooManyItems, shipping)),
                Arguments.of("promoCodes is missing", requestJson(validItems, shipping)),
                Arguments.of("item quantity is zero", requestJson(promoCodes, "\"items\": [" + item(KEYBOARD, 0, "10.00") + "]", shipping)),
                Arguments.of("item quantity is negative", requestJson(promoCodes, "\"items\": [" + item(KEYBOARD, -2, "10.00") + "]", shipping)),
                Arguments.of("item productExternalId is missing", requestJson(promoCodes, "\"items\": [{\"quantity\": 1, \"unitPrice\": 10.00}]", shipping)),
                Arguments.of("item unitPrice is missing", requestJson(promoCodes, "\"items\": [{\"productExternalId\": \"" + KEYBOARD + "\", \"quantity\": 1}]", shipping)),
                Arguments.of("item unitPrice is negative", requestJson(promoCodes, "\"items\": [" + item(KEYBOARD, 1, "-0.01") + "]", shipping)),
                Arguments.of("shippingCost is missing", requestJson(promoCodes, validItems)),
                Arguments.of("shippingCost is negative", requestJson(promoCodes, validItems, "\"shippingCost\": -1.00"))
            );
        }

        @Test
        @DisplayName("Should fail with 400 Bad Request when the request body is not valid JSON")
        void evaluate_malformedJson_returnsBadRequest() throws Exception {
            evaluate(STOREFRONT_ID, "{ \"items\": [ ").andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should fail with 400 Bad Request when the X-Storefront-Id header is missing")
        void evaluate_missingStorefrontHeader_returnsBadRequest() throws Exception {
            mockMvc.perform(post(EVALUATE_URL_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(cartPayload(NO_PROMO_CODES)))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Authorization Tests")
    class AuthorizationTests {

        @Test
        @WithMockJwt(roles = Roles.INTEGRATION_CLIENT)
        @DisplayName("Should evaluate the order when the caller is an integration client")
        void evaluate_integrationClient_returnsOk() throws Exception {
            JsonNode response = evaluateOk(cartPayload(NO_PROMO_CODES));

            assertThat(decimal(response, "oldTotal")).isEqualByComparingTo("265.00");
        }

        @Test
        @WithMockJwt(roles = Roles.PROMOTION_MANAGER)
        @DisplayName("Should evaluate the order when the caller is a promotion manager")
        void evaluate_promotionManager_returnsOk() throws Exception {
            JsonNode response = evaluateOk(cartPayload(NO_PROMO_CODES));

            assertThat(decimal(response, "oldTotal")).isEqualByComparingTo("265.00");
        }
    }

    @Nested
    @DisplayName("Transaction Boundary Tests")
    class TransactionBoundaryTests {

        @Test
        @DisplayName("Should not hold a database connection while waiting for another caller to load the rules")
        void evaluate_waitingForRulesLock_holdsNoDatabaseConnection() throws Exception {
            // Held by the test, as if another caller were loading this storefront's rules right now.
            Lock rulesLock = cacheLockRegistry.obtain(PromotionRuleCacheReader.RULES_CACHE_NAME + "::" + STOREFRONT_ID);
            // The evaluation runs on another thread, which doesn't see the test's @WithMockJwt context.
            SecurityContext callerContext = SecurityContextHolder.getContext();
            HikariPoolMXBean connectionPool = dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean();

            AtomicReference<Thread> evaluatingThread = new AtomicReference<>();

            rulesLock.lock();
            try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
                Future<Integer> evaluation = executor.submit(() -> {
                    evaluatingThread.set(Thread.currentThread());
                    return mockMvc.perform(post(EVALUATE_URL_PATH)
                            .with(securityContext(callerContext))
                            .header("X-Storefront-Id", STOREFRONT_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cartPayload(NO_PROMO_CODES)))
                        .andReturn()
                        .getResponse()
                        .getStatus();
                });

                try {
                    await("evaluation waiting for the rules lock")
                        .atMost(10, TimeUnit.SECONDS)
                        .until(() -> isWaitingForCacheLock(evaluatingThread.get()));

                    assertThat(evaluation).isNotDone();
                    // Awaited, because background work such as the usage expiration job may hold a connection for a moment.
                    await("all database connections returned to the pool")
                        .atMost(2, TimeUnit.SECONDS)
                        .until(() -> connectionPool.getActiveConnections() == 0);
                } finally {
                    rulesLock.unlock();
                }

                assertThat(evaluation.get(10, TimeUnit.SECONDS)).isEqualTo(HttpStatus.OK.value());
            }
        }

        /** True once the thread is inside {@link RedisLockRegistry}'s lock, which the test holds, so it can't get past it. */
        private boolean isWaitingForCacheLock(Thread thread) {
            return thread != null && Arrays.stream(thread.getStackTrace())
                .anyMatch(frame -> frame.getClassName().startsWith(RedisLockRegistry.class.getName()));
        }
    }

    private ResultActions evaluate(Long storefrontId, String payload) throws Exception {
        return mockMvc.perform(post(EVALUATE_URL_PATH)
            .header("X-Storefront-Id", storefrontId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload));
    }

    private JsonNode evaluateOk(String payload) throws Exception {
        MvcResult result = evaluate(STOREFRONT_ID, payload)
            .andExpect(status().isOk())
            .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode evaluateOkUnchecked(String payload) {
        try {
            return evaluateOk(payload);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Cart of 2 keyboards @ 100.00 + 1 mouse @ 50.00 with 15.00 shipping (old total 265.00). */
    private static String cartPayload(String promoCodes) {
        return requestJson(
            "\"sessionId\": \"sess-1\"",
            "\"customerId\": \"cust-1\"",
            "\"promoCodes\": " + promoCodes,
            "\"items\": [" + item(KEYBOARD, 2, "100.00") + "," + item(MOUSE, 1, "50.00") + "]",
            "\"shippingCost\": 15.00"
        );
    }

    private static String requestJson(String... fields) {
        return "{" + String.join(",", fields) + "}";
    }

    private static String item(String productExternalId, int quantity, String unitPrice) {
        return """
            {"productExternalId": "%s", "quantity": %d, "unitPrice": %s}""".formatted(productExternalId, quantity, unitPrice);
    }

    private PromotionRule saveRule(
        Long storefrontId,
        String name,
        int priority,
        boolean shortCircuit,
        boolean active,
        ConditionNode condition,
        ActionStrategy action
    ) {
        PromotionRule rule = new PromotionRule();
        rule.setStorefrontId(storefrontId);
        rule.setName(name);
        rule.setPriority(priority);
        rule.setShortCircuit(shortCircuit);
        rule.setActive(active);
        rule.setConditionTree(condition);
        rule.setAction(action);

        return promotionRepository.saveAndFlush(rule);
    }

    private PromotionRule saveLimitedRule(String name, String promoCode, int usageLimit) {
        PromotionRule rule = new PromotionRule();
        rule.setStorefrontId(STOREFRONT_ID);
        rule.setName(name);
        rule.setPriority(100);
        rule.setShortCircuit(false);
        rule.setActive(true);
        rule.setHasUsageLimit(true);
        rule.setUsageLimit(usageLimit);
        rule.setRemainingUses(usageLimit);
        rule.setConditionTree(new PromoCodeConditionNode(promoCode, Operator.EQUALS));
        rule.setAction(new ProductPercentageDiscountAction(KEYBOARD, new BigDecimal("0.10")));

        return promotionRepository.saveAndFlush(rule);
    }

    private int remainingUses(PromotionRule rule) {
        return promotionRepository.findById(rule.getId()).orElseThrow().getRemainingUses();
    }

    private Product createTestProduct(String externalId, String sku, Long storefrontId, Set<Long> categoryIds) {
        return Product.builder()
            .storefrontId(storefrontId)
            .sku(sku)
            .externalId(externalId)
            .name("Test Product " + externalId)
            .categoryIds(new HashSet<>(categoryIds))
            .build();
    }

    private static BigDecimal decimal(JsonNode node, String fieldName) {
        return node.path(fieldName).decimalValue();
    }

    private static JsonNode line(JsonNode response, String productExternalId) {
        for (JsonNode item : response.path("items")) {
            if (productExternalId.equals(item.path("productExternalId").asString())) {
                return item;
            }
        }
        throw new AssertionError("No line item for product " + productExternalId + " in " + response);
    }

    private static List<Long> ruleIds(JsonNode response, String fieldName) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode rule : response.path(fieldName)) {
            ids.add(rule.path("ruleId").asLong());
        }
        return ids;
    }

    private static List<String> extractStringList(JsonNode parentNode, String fieldName) {
        List<String> list = new ArrayList<>();
        JsonNode arrayNode = parentNode.path(fieldName);
        if (arrayNode.isArray()) {
            for (JsonNode element : arrayNode) {
                list.add(element.asString());
            }
        }
        return list;
    }
}
