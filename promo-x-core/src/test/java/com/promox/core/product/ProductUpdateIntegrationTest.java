package com.promox.core.product;

import com.promox.core.category.CategoryDto;
import com.promox.core.category.internal.persistence.Category;
import com.promox.core.category.internal.persistence.CategoryRepository;
import com.promox.core.infrastructure.security.Roles;
import com.promox.core.product.event.ProductUpdatedEvent;
import com.promox.core.product.internal.dto.UpdateProductRequest;
import com.promox.core.product.internal.persistence.Product;
import com.promox.core.product.internal.persistence.ProductRepository;
import com.promox.core.product.internal.service.ProductLookupApiImpl;
import com.promox.core.shared.IntegrationTestBase;
import com.promox.core.storefront.internal.persistence.Storefront;
import com.promox.core.storefront.internal.persistence.StorefrontRepository;

import com.promox.core.testfixtures.security.WithMockJwt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.modulith.test.EnableScenarios;
import org.springframework.modulith.test.Scenario;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WithMockJwt(roles = Roles.ADMIN)
@EnableScenarios
public class ProductUpdateIntegrationTest extends IntegrationTestBase {
    private static final String UPDATE_URL = "/product/{productId}";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private StorefrontRepository storefrontRepository;

    @Autowired
    private ProductLookupApi productLookupApi;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private Storefront activeStorefront;
    private Category keyboards;
    private Product existingProduct;

    @BeforeEach
    void setUp() {
        activeStorefront = storefrontRepository.save(
            new Storefront(null, "Main Storefront", "https://mainstore.com")
        );
        keyboards = createCategory(activeStorefront, "Keyboards");
        existingProduct = saveProduct(activeStorefront, "EXT-KB-01", Set.of(keyboards.getId()));
    }

    @Nested
    @DisplayName("Product Update Happy Path Scenarios")
    class HappyPathTests {

        @Test
        @DisplayName("Should update all fields and replace the product's categories")
        void shouldUpdateAllFields_andReplaceCategories() throws Exception {
            Category mice = createCategory(activeStorefront, "Mice");
            Category gaming = createCategory(activeStorefront, "Gaming");
            var request = new UpdateProductRequest(
                "EXT-MS-01", "SKU-MOUSE", "Gaming Mouse", Set.of(mice.getId(), gaming.getId())
            );

            update(existingProduct.getId(), request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(existingProduct.getId()))
                .andExpect(jsonPath("$.externalId").value("EXT-MS-01"))
                .andExpect(jsonPath("$.sku").value("SKU-MOUSE"))
                .andExpect(jsonPath("$.name").value("Gaming Mouse"))
                .andExpect(jsonPath("$.storefrontId").value(activeStorefront.getId()))
                .andExpect(jsonPath("$.categoryIds", containsInAnyOrder(
                    mice.getId().intValue(), gaming.getId().intValue()
                )));

            Product updated = reload(existingProduct);
            assertThat(updated.getExternalId()).isEqualTo("EXT-MS-01");
            assertThat(updated.getSku()).isEqualTo("SKU-MOUSE");
            assertThat(updated.getName()).isEqualTo("Gaming Mouse");
            assertThat(updated.getCategoryIds()).containsExactlyInAnyOrder(mice.getId(), gaming.getId());
        }

        @Test
        @DisplayName("Should allow keeping the same external ID")
        void shouldUpdate_whenExternalIdIsUnchanged() throws Exception {
            var request = new UpdateProductRequest("EXT-KB-01", "SKU-NEW", "Renamed Keyboard", Set.of(keyboards.getId()));

            update(existingProduct.getId(), request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.externalId").value("EXT-KB-01"))
                .andExpect(jsonPath("$.name").value("Renamed Keyboard"));
        }

        @Test
        @DisplayName("Should remove all categories when categoryIds is omitted")
        void shouldClearCategories_whenCategoryIdsIsOmitted() throws Exception {
            String payload = """
                {"externalId": "EXT-KB-01", "sku": "SKU-KB", "name": "Keyboard"}
                """;

            mockMvc.perform(put(UPDATE_URL, existingProduct.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryIds", empty()));

            assertThat(reload(existingProduct).getCategoryIds()).isEmpty();
        }

        @Test
        @DisplayName("Should allow an external ID that is used by a product in a different storefront")
        void shouldUpdate_whenExternalIdExistsInDifferentStorefront() throws Exception {
            Storefront otherStorefront = storefrontRepository.save(
                new Storefront(null, "Other Storefront", "https://otherstore.com")
            );
            saveProduct(otherStorefront, "EXT-SHARED", Set.of());

            update(existingProduct.getId(), new UpdateProductRequest("EXT-SHARED", "SKU-KB", "Keyboard", Set.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.externalId").value("EXT-SHARED"));
        }
    }

    @Nested
    @DisplayName("Product Update Event And Cache Scenarios")
    class EventAndCacheTests {

        @Test
        @DisplayName("Should publish ProductUpdatedEvent with the previous and the new external ID")
        void shouldPublishProductUpdatedEvent(Scenario scenario) {
            var request = new UpdateProductRequest("EXT-KB-02", "SKU-KB-02", "Keyboard v2", Set.of(keyboards.getId()));

            scenario.stimulate(() -> updateUnchecked(existingProduct.getId(), request))
                .andWaitForEventOfType(ProductUpdatedEvent.class)
                .matchingMappedValue(ProductUpdatedEvent::id, existingProduct.getId())
                .toArriveAndVerify(event -> {
                    assertThat(event.previousExternalId()).isEqualTo("EXT-KB-01");
                    assertThat(event.externalId()).isEqualTo("EXT-KB-02");
                    assertThat(event.sku()).isEqualTo("SKU-KB-02");
                    assertThat(event.name()).isEqualTo("Keyboard v2");
                    assertThat(event.categoryIds()).containsExactly(keyboards.getId());
                    assertThat(event.storefrontId()).isEqualTo(activeStorefront.getId());
                });
        }

        @Test
        @DisplayName("Should evict the cached product stored under its previous external ID")
        void shouldEvictCachedProduct_whenExternalIdChanges(Scenario scenario) {
            lookup("EXT-KB-01");
            assertThat(isCached("EXT-KB-01")).isTrue();

            var request = new UpdateProductRequest("EXT-KB-02", "SKU-KB", "Keyboard", Set.of(keyboards.getId()));

            scenario.stimulate(() -> updateUnchecked(existingProduct.getId(), request))
                .andWaitForStateChange(() -> isCached("EXT-KB-01"), cached -> !cached)
                .andVerify(cached -> {
                    assertThat(lookup("EXT-KB-01")).isEmpty();
                    assertThat(lookup("EXT-KB-02"))
                        .extracting(product -> product.base().externalId())
                        .containsExactly("EXT-KB-02");
                });
        }

        @Test
        @DisplayName("Should evict the cached product so the next lookup returns the updated data")
        void shouldEvictCachedProduct_whenExternalIdIsUnchanged(Scenario scenario) {
            Category mice = createCategory(activeStorefront, "Mice");
            lookup("EXT-KB-01");

            var request = new UpdateProductRequest("EXT-KB-01", "SKU-KB", "Keyboard Pro", Set.of(mice.getId()));

            scenario.stimulate(() -> updateUnchecked(existingProduct.getId(), request))
                .andWaitForStateChange(() -> isCached("EXT-KB-01"), cached -> !cached)
                .andVerify(cached -> {
                    ProductWithCategoriesDto product = lookup("EXT-KB-01").getFirst();
                    assertThat(product.base().name()).isEqualTo("Keyboard Pro");
                    assertThat(product.categories()).extracting(CategoryDto::name).containsExactly("Mice");
                });
        }
    }

    @Nested
    @DisplayName("Product Update Business Exception Scenarios")
    class BusinessExceptionTests {

        @Test
        @DisplayName("Should return 404 when the product does not exist")
        void shouldFail_whenProductDoesNotExist() throws Exception {
            update(existingProduct.getId() + 9999L, new UpdateProductRequest("EXT-X", "SKU-X", "Name", Set.of()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        }

        @Test
        @DisplayName("Should fail when the new external ID is used by another product in the same storefront")
        void shouldFail_whenExternalIdBelongsToAnotherProduct() throws Exception {
            saveProduct(activeStorefront, "EXT-TAKEN", Set.of());

            update(existingProduct.getId(), new UpdateProductRequest("EXT-TAKEN", "SKU-KB", "Keyboard", Set.of()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXTERNAL_ID_ALREADY_EXISTS"));

            assertThat(reload(existingProduct).getExternalId()).isEqualTo("EXT-KB-01");
        }

        @Test
        @DisplayName("Should fail when one category exists but another does not")
        void shouldFail_whenSomeCategoriesDoNotExist() throws Exception {
            Long missingCategoryId = keyboards.getId() + 9999L;

            update(existingProduct.getId(), new UpdateProductRequest(
                "EXT-KB-01", "SKU-KB", "Keyboard", Set.of(keyboards.getId(), missingCategoryId)
            ))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_IDS_NOT_FOUND"))
                .andExpect(jsonPath("$.errors[0].path").value("categoryIds"))
                .andExpect(jsonPath("$.errors[0].errorContext.missingCategoryIds[0]").value(missingCategoryId))
                .andExpect(jsonPath("$.errors[0].errorContext.existingCategoryIds[0]").value(keyboards.getId()));

            assertThat(reload(existingProduct).getCategoryIds()).containsExactly(keyboards.getId());
        }

        @Test
        @DisplayName("Should fail when a category belongs to a different storefront")
        void shouldFail_whenCategoryBelongsToAnotherStorefront() throws Exception {
            Storefront otherStorefront = storefrontRepository.save(
                new Storefront(null, "Other Storefront", "https://otherstore.com")
            );
            Category foreignCategory = createCategory(otherStorefront, "Foreign");

            update(existingProduct.getId(), new UpdateProductRequest(
                "EXT-KB-01", "SKU-KB", "Keyboard", Set.of(foreignCategory.getId())
            ))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_IDS_NOT_FOUND"));

            assertThat(reload(existingProduct).getCategoryIds()).containsExactly(keyboards.getId());
        }
    }

    @Nested
    @DisplayName("Product Update Authorization Scenarios")
    class AuthorizationTests {

        @Test
        @WithMockJwt(roles = {Roles.EMPLOYEE, Roles.AUDITOR, Roles.PROMOTION_MANAGER})
        @DisplayName("Should return 403 and leave the product unchanged when the user is not an admin")
        void shouldFail_whenUserIsNotAdmin() throws Exception {
            update(existingProduct.getId(), new UpdateProductRequest("EXT-MS-01", "SKU-MOUSE", "Gaming Mouse", Set.of()))
                .andExpect(status().isForbidden());

            Product unchanged = reload(existingProduct);
            assertThat(unchanged.getExternalId()).isEqualTo("EXT-KB-01");
            assertThat(unchanged.getName()).isEqualTo("Product EXT-KB-01");
            assertThat(unchanged.getCategoryIds()).containsExactly(keyboards.getId());
        }
    }

    @Nested
    @DisplayName("Validation Scenarios")
    class ValidationTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidPayloads")
        @DisplayName("Should return 400 when the request body is invalid")
        void shouldFail_whenRequestIsInvalid(String scenario, String payload) throws Exception {
            mockMvc.perform(put(UPDATE_URL, existingProduct.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
                .andExpect(status().isBadRequest());

            assertThat(reload(existingProduct).getName()).isEqualTo("Product EXT-KB-01");
        }

        static Stream<Arguments> invalidPayloads() {
            String tooLong = "x".repeat(256);
            return Stream.of(
                Arguments.of("externalId is missing", """
                    {"sku": "SKU", "name": "Name"}"""),
                Arguments.of("externalId is blank", """
                    {"externalId": "  ", "sku": "SKU", "name": "Name"}"""),
                Arguments.of("sku is blank", """
                    {"externalId": "EXT", "sku": "", "name": "Name"}"""),
                Arguments.of("name is missing", """
                    {"externalId": "EXT", "sku": "SKU"}"""),
                Arguments.of("name is longer than 255 characters", """
                    {"externalId": "EXT", "sku": "SKU", "name": "%s"}""".formatted(tooLong)),
                Arguments.of("category ID is negative", """
                    {"externalId": "EXT", "sku": "SKU", "name": "Name", "categoryIds": [-1]}"""),
                Arguments.of("category ID is null", """
                    {"externalId": "EXT", "sku": "SKU", "name": "Name", "categoryIds": [null]}""")
            );
        }
    }

    private ResultActions update(Long productId, UpdateProductRequest request) throws Exception {
        return mockMvc.perform(put(UPDATE_URL, productId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));
    }

    private void updateUnchecked(Long productId, UpdateProductRequest request) {
        try {
            update(productId, request).andExpect(status().isOk());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private List<ProductWithCategoriesDto> lookup(String externalId) {
        return productLookupApi.findAllWithCategoriesByExternalIds(Set.of(externalId), activeStorefront.getId());
    }

    private boolean isCached(String externalId) {
        String key = ProductLookupApiImpl.PRODUCTS_WITH_CATEGORIES_CACHE + "::"
            + ProductLookupApiImpl.productCacheKey(activeStorefront.getId(), externalId);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    private Product reload(Product product) {
        return productRepository.findWithCategoryById(product.getId()).orElseThrow();
    }

    private Product saveProduct(Storefront storefront, String externalId, Set<Long> categoryIds) {
        return productRepository.save(Product.builder()
            .storefrontId(storefront.getId())
            .externalId(externalId)
            .sku("SKU-" + externalId)
            .name("Product " + externalId)
            .categoryIds(new HashSet<>(categoryIds))
            .build());
    }

    private Category createCategory(Storefront storefront, String name) {
        Category category = new Category();
        category.setExternalId("EXT-" + name + "-" + storefront.getId());
        category.setName(name);
        category.setStorefrontId(storefront.getId());
        return categoryRepository.save(category);
    }
}
