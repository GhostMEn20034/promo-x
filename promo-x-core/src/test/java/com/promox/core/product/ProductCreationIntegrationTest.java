package com.promox.core.product;

import com.promox.core.category.internal.persistence.Category;
import com.promox.core.category.internal.persistence.CategoryRepository;
import com.promox.core.product.internal.dto.CreateProductRequest;
import com.promox.core.product.internal.persistence.Product;
import com.promox.core.product.internal.persistence.ProductRepository;
import com.promox.core.shared.IntegrationTestBase;

import com.promox.core.storefront.internal.persistence.Storefront;
import com.promox.core.storefront.internal.persistence.StorefrontRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


public class ProductCreationIntegrationTest extends IntegrationTestBase {
    private static final String STOREFRONT_HEADER = "X-Storefront-Id";
    private static final String BASE_URL = "/product";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private StorefrontRepository storefrontRepository;

    private Storefront activeStorefront;

    @BeforeEach
    void setUpStorefront() {
        activeStorefront = storefrontRepository.save(
            new Storefront(null, "Main Storefront", "https://mainstore.com")
        );
    }

    @Nested
    @DisplayName("Product Creation Happy Path Scenarios")
    class HappyPathTests {

        @Test
        @DisplayName("Should create product with real existing categories in DB")
        void shouldCreateProduct_whenCategoriesExistInDb() throws Exception {
            // Given
            Category electronics = createCategory("Electronics");
            Category smartphones = createCategory("Smartphones");

            Set<Long> categoryIds = Set.of(electronics.getId(), smartphones.getId());

            CreateProductRequest request = new CreateProductRequest(
                "EXT-1001",
                "SKU-IPHONE-15",
                "iPhone 15 Pro",
                categoryIds
            );

            // When
            String responseJson = mockMvc.perform(post(BASE_URL + "/")
                    .header(STOREFRONT_HEADER, activeStorefront.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andReturn().getResponse().getContentAsString();

            // Assert JSON Response Content using AssertJ
            DefaultProductDto responseDto = objectMapper.readValue(responseJson, DefaultProductDto.class);
            assertThat(responseDto.base().id()).isNotNull();
            assertThat(responseDto.base().externalId()).isEqualTo("EXT-1001");
            assertThat(responseDto.base().sku()).isEqualTo("SKU-IPHONE-15");
            assertThat(responseDto.base().name()).isEqualTo("iPhone 15 Pro");
            assertThat(responseDto.categoryIds())
                .containsExactlyInAnyOrder(electronics.getId(), smartphones.getId());
            assertThat(responseDto.base().storefrontId()).isEqualTo(activeStorefront.getId());

            // Assert Database Persistence
            Product savedProduct = productRepository.findWithCategoryById(responseDto.base().id()).orElseThrow();
            assertThat(savedProduct.getSku()).isEqualTo("SKU-IPHONE-15");
            assertThat(savedProduct.getStorefrontId()).isEqualTo(activeStorefront.getId());
            assertThat(savedProduct.getCategoryIds())
                .containsExactlyInAnyOrder(electronics.getId(), smartphones.getId());
        }

        @Test
        @DisplayName("Should create product when categoryIds is empty")
        void shouldCreateProduct_whenCategoryIdsIsEmpty() throws Exception {
            CreateProductRequest request = new CreateProductRequest(
                "EXT-1002",
                "SKU-NO-CAT",
                "Uncategorized Item",
                Set.of()
            );

            mockMvc.perform(post(BASE_URL+ "/")
                    .header(STOREFRONT_HEADER, activeStorefront.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryIds.length()").value(0));

            assertThat(productRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should allow same externalId if it exists in a different storefront")
        void shouldCreateProduct_whenSameExternalIdExistsInDifferentStorefront() throws Exception {
            // Given: Product with same externalId exists in another storefront
            String sharedExternalId = "EXT-SHARED";

            Storefront otherStorefront = storefrontRepository.save(
                new Storefront(null, "Other Storefront", "https://otherstore.com")
            );

            productRepository.save(Product.builder()
                .externalId(sharedExternalId)
                .sku("SKU-OTHER-STORE")
                .name("Other Store Product")
                .storefrontId(otherStorefront.getId())
                .categoryIds(Set.of())
                .build());

            CreateProductRequest request = new CreateProductRequest(
                sharedExternalId,
                "SKU-ACTIVE-STORE",
                "Active Store Product",
                Set.of()
            );

            // When / Then: Creation in activeStorefront should succeed
            mockMvc.perform(post(BASE_URL + "/")
                    .header(STOREFRONT_HEADER, activeStorefront.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.externalId").value(sharedExternalId));

            assertThat(productRepository.count()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Product Creation Business Exception Scenarios")
    class BusinessExceptionTests {

        @Test
        @DisplayName("Should fail when one category exists but another does not")
        void shouldFail_whenSomeCategoriesDoNotExist() throws Exception {
            // Given
            Category realCategory = createCategory("Laptops");
            Long missingCategoryId = realCategory.getId() + 9999L;

            Set<Long> requestedCategoryIds = Set.of(realCategory.getId(), missingCategoryId);

            CreateProductRequest request = new CreateProductRequest(
                "EXT-1003",
                "SKU-INVALID-CAT",
                "Invalid Category Product",
                requestedCategoryIds
            );

            // When / Then
            mockMvc.perform(post(BASE_URL + "/")
                    .header(STOREFRONT_HEADER, activeStorefront.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_IDS_NOT_FOUND"))
                .andExpect(jsonPath("$.errors[0].path").value("categoryIds"))
                .andExpect(jsonPath("$.errors[0].errorContext.missingCategoryIds[0]").value(missingCategoryId))
                .andExpect(jsonPath("$.errors[0].errorContext.existingCategoryIds[0]").value(realCategory.getId()));

            assertThat(productRepository.count()).isZero();
        }
    }

    @Nested
    @DisplayName("Validation Scenarios")
    class ValidationTests {

        @Test
        @DisplayName("Should return 400 when storefront header is missing")
        void shouldFail_whenStorefrontHeaderIsMissing() throws Exception {
            CreateProductRequest request = new CreateProductRequest(
                "EXT-1", "SKU-1", "Product Name", Set.of()
            );

            mockMvc.perform(post(BASE_URL + "/")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when required fields are blank")
        void shouldFail_whenSkuOrNameIsBlank() throws Exception {
            CreateProductRequest invalidRequest = new CreateProductRequest(
                "EXT-1",
                "",
                "   ",
                Set.of()
            );

            mockMvc.perform(post(BASE_URL + "/")
                    .header(STOREFRONT_HEADER, activeStorefront.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should create product when optional externalId is null")
        void shouldFail_whenExternalIdIsNull() throws Exception {
            CreateProductRequest request = new CreateProductRequest(
                null,
                "SKU-NO-EXT",
                "Basic Product",
                Set.of()
            );

            mockMvc.perform(post(BASE_URL + "/")
                    .header(STOREFRONT_HEADER, activeStorefront.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        }
    }

    private Category createCategory(String name) {
        Category category = new Category();
        category.setExternalId("EXT-" + name);
        category.setName(name);
        category.setStorefrontId(activeStorefront.getId());
        return categoryRepository.save(category);
    }
}
