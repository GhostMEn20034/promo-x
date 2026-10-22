package com.promox.core.product;

import com.promox.core.category.internal.persistence.Category;
import com.promox.core.category.internal.persistence.CategoryRepository;
import com.promox.core.infrastructure.security.Roles;
import com.promox.core.product.internal.dto.CreateMultipleProductsRequest;
import com.promox.core.product.internal.dto.CreateProductRequest;
import com.promox.core.product.internal.persistence.Product;
import com.promox.core.product.internal.persistence.ProductRepository;
import com.promox.core.shared.IntegrationTestBase;
import com.promox.core.storefront.internal.persistence.Storefront;
import com.promox.core.storefront.internal.persistence.StorefrontRepository;

import com.promox.core.testfixtures.security.WithMockJwt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

@WithMockJwt(roles = Roles.EMPLOYEE)
public class ProductBulkInsertIntegrationTest extends IntegrationTestBase {
    private static final String BULK_INSERT_URL = "/bulk-insert";
    private static final String STOREFRONT_HEADER = "X-Storefront-Id";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StorefrontRepository storefrontRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private Long defaultStorefrontId;
    private Long category10Id;
    private Long category20Id;

    @BeforeEach
    void setupDatabaseData() {
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        storefrontRepository.deleteAll();

        Storefront storefront = storefrontRepository.save(
            Storefront.builder()
                .name("Default Storefront")
                .build()
        );
        this.defaultStorefrontId = storefront.getId();

        List<Category> savedCategories = categoryRepository.saveAll(List.of(
            Category.builder().externalId("CAT-10").name("Category 10").storefrontId(defaultStorefrontId).build(),
            Category.builder().externalId("CAT-20").name("Category 20").storefrontId(defaultStorefrontId).build()
        ));

        this.category10Id = savedCategories.get(0).getId();
        this.category20Id = savedCategories.get(1).getId();
    }

    @Nested
    @DisplayName("Happy Path Integration Tests")
    class HappyPathTests {

        @Test
        @DisplayName("Should successfully insert 250 products across multiple batch chunks (> 200 BATCH_SIZE) into database")
        void createMultipleProducts_successfulBulkInsert_persistsAllProductsInDatabase() throws Exception {
            int totalProducts = 250;
            List<CreateProductRequest> items = generateProductRequests(totalProducts);
            var request = new CreateMultipleProductsRequest(items);

            mockMvc.perform(post("/product" + BULK_INSERT_URL)
                    .header(STOREFRONT_HEADER, defaultStorefrontId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

            assertThat(productRepository.countByStorefrontId(defaultStorefrontId)).isEqualTo(totalProducts);

            Product persistedProduct = productRepository.findByExternalId("EXT-249").orElseThrow();
            assertThat(persistedProduct.getName()).isEqualTo("Product 249");
        }
    }

    @Nested
    @DisplayName("Business Exception Integration Tests")
    class BusinessExceptionTests {

        @Test
        @DisplayName("Should return 409 Conflict when storefront does not exist in database")
        void createMultipleProducts_nonExistentStorefront_returnsConflict() throws Exception {
            Long missingStorefrontId = 99999L;

            List<CreateProductRequest> items = generateProductRequests(2);
            var request = new CreateMultipleProductsRequest(items);

            mockMvc.perform(post("/product" + BULK_INSERT_URL)
                    .header(STOREFRONT_HEADER, missingStorefrontId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STOREFRONT"))
                .andExpect(jsonPath("$.title").value("Business rule violation"));

            assertThat(productRepository.count()).isZero();
        }

        @Test
        @DisplayName("Should return 409 Conflict when external IDs are duplicated within request payload")
        void createMultipleProducts_duplicateExternalIdsInBatch_returnsConflict() throws Exception {
            List<CreateProductRequest> items = List.of(
                new CreateProductRequest("EXT-DUP", "SKU-1", "Product 1", Set.of(category10Id)),
                new CreateProductRequest("EXT-DUP", "SKU-2", "Product 2", Set.of(category10Id))
            );
            var request = new CreateMultipleProductsRequest(items);

            mockMvc.perform(post("/product" + BULK_INSERT_URL)
                    .header(STOREFRONT_HEADER, defaultStorefrontId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXTERNAL_ID_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].code").value("DUPLICATE_EXTERNAL_ID_IN_BATCH"))
                .andExpect(jsonPath("$.errors[0].path").value("items[1].externalId"));

            assertThat(productRepository.count()).isZero();
        }

        @Test
        @DisplayName("Should return 409 Conflict when external ID already exists in the db")
        void createMultipleProducts_externalIdAlreadyExistsInDb_returnsConflict() throws Exception {
            productRepository.save(
                Product.builder()
                    .externalId("EXT-EXISTING")
                    .sku("SKU-000")
                    .name("Existing Product")
                    .storefrontId(defaultStorefrontId)
                    .build()
            );

            List<CreateProductRequest> items = List.of(
                new CreateProductRequest("EXT-NEW", "SKU-1", "New Product", Set.of(category10Id)),
                new CreateProductRequest("EXT-EXISTING", "SKU-2", "Conflicting Product", Set.of(category10Id))
            );
            var request = new CreateMultipleProductsRequest(items);

            mockMvc.perform(post("/product" + BULK_INSERT_URL)
                    .header(STOREFRONT_HEADER, defaultStorefrontId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXTERNAL_ID_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].code").value("EXTERNAL_ID_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.errors[0].path").value("items[1].externalId"));

            assertThat(productRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should return 409 Conflict when category ID does not exist in the db")
        void createMultipleProducts_nonExistentCategoryInDb_returnsConflict() throws Exception {
            Long unmappedCategoryId = 99999L;

            List<CreateProductRequest> items = List.of(
                new CreateProductRequest("EXT-1", "SKU-1", "Product 1", Set.of(category10Id, unmappedCategoryId))
            );
            var request = new CreateMultipleProductsRequest(items);

            mockMvc.perform(post("/product" + BULK_INSERT_URL)
                    .header(STOREFRONT_HEADER, defaultStorefrontId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_IDS_NOT_FOUND"))
                .andExpect(jsonPath("$.errors[0].path").value("items[0].categoryIds"));

            assertThat(productRepository.count()).isZero();
        }
    }

    @Nested
    @DisplayName("Validation Integration Tests")
    class ValidationTests {

        @Test
        @DisplayName("Should return 400 Bad Request when request body violates Jakarta Bean Validation constraints")
        void createMultipleProducts_invalidBeanValidation_returnsBadRequest() throws Exception {
            List<CreateProductRequest> items = List.of(
                new CreateProductRequest("EXT-1", "", "Product 1", Set.of(category10Id))
            );
            var request = new CreateMultipleProductsRequest(items);

            mockMvc.perform(post("/product" + BULK_INSERT_URL)
                    .header(STOREFRONT_HEADER, defaultStorefrontId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

            assertThat(productRepository.count()).isZero();
        }

        @Test
        @DisplayName("Should return 400 Bad Request when items list is empty")
        void createMultipleProducts_emptyItemsList_returnsBadRequest() throws Exception {
            var request = new CreateMultipleProductsRequest(List.of());

            mockMvc.perform(post("/product" + BULK_INSERT_URL)
                    .header(STOREFRONT_HEADER, defaultStorefrontId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

            assertThat(productRepository.count()).isZero();
        }
    }

    @Nested
    @DisplayName("Authorization Integration Tests")
    class AuthorizationTests {

        @Test
        @WithMockJwt(roles = {Roles.ADMIN, Roles.AUDITOR, Roles.PROMOTION_MANAGER})
        @DisplayName("Should return 403 Forbidden and insert nothing when the user has no employee role")
        void createMultipleProducts_userIsNotEmployee_returnsForbidden() throws Exception {
            var request = new CreateMultipleProductsRequest(generateProductRequests(2));

            mockMvc.perform(post("/product" + BULK_INSERT_URL)
                    .header(STOREFRONT_HEADER, defaultStorefrontId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

            assertThat(productRepository.count()).isZero();
        }
    }

    private List<CreateProductRequest> generateProductRequests(int count) {
        return IntStream.range(0, count)
            .mapToObj(i -> new CreateProductRequest(
                "EXT-" + i,
                "SKU-" + i,
                "Product " + i,
                Set.of(category10Id, category20Id)
            ))
            .toList();
    }
}
