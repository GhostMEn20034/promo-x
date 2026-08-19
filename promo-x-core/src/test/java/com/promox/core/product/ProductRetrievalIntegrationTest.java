package com.promox.core.product;

import com.promox.core.category.internal.persistence.Category;
import com.promox.core.category.internal.persistence.CategoryRepository;
import com.promox.core.product.internal.persistence.Product;
import com.promox.core.product.internal.persistence.ProductRepository;
import com.promox.core.shared.IntegrationTestBase;
import com.promox.core.storefront.internal.persistence.Storefront;
import com.promox.core.storefront.internal.persistence.StorefrontRepository;

import org.springframework.beans.factory.annotation.Autowired;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.HashSet;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


public class ProductRetrievalIntegrationTest extends IntegrationTestBase {
    private static final String BASE_URL = "/product";

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
    @DisplayName("Product Retrieval Happy Path Scenarios")
    class HappyPathTests {

        @Test
        @DisplayName("Should successfully retrieve product with flattened base fields and mapped categories")
        void shouldReturnProductWithCategories_whenProductExists() throws Exception {
            // Given
            Category electronics = createCategory("Electronics");
            Category smartphones = createCategory("Smartphones");

            Product product = productRepository.save(Product.builder()
                .externalId("EXT-GET-1001")
                .sku("SKU-IPHONE-15")
                .name("iPhone 15 Pro")
                .storefrontId(activeStorefront.getId())
                .categoryIds(new HashSet<>(Set.of(electronics.getId(), smartphones.getId())))
                .build());

            // When / Then
            mockMvc.perform(get(BASE_URL + "/{productId}", product.getId())
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // Assert unwrapped ProductBaseDto properties flattened at root JSON level
                .andExpect(jsonPath("$.id").value(product.getId()))
                .andExpect(jsonPath("$.externalId").value("EXT-GET-1001"))
                .andExpect(jsonPath("$.sku").value("SKU-IPHONE-15"))
                .andExpect(jsonPath("$.name").value("iPhone 15 Pro"))
                .andExpect(jsonPath("$.storefrontId").value(activeStorefront.getId()))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                // Assert categories array length
                .andExpect(jsonPath("$.categories.length()").value(2))
                // Assert category items by ID using order-independent JsonPath filters
                .andExpect(jsonPath("$.categories[?(@.id == " + electronics.getId() + ")].name").value("Electronics"))
                .andExpect(jsonPath("$.categories[?(@.id == " + smartphones.getId() + ")].name").value("Smartphones"));
        }

        @Test
        @DisplayName("Should retrieve product with empty categories list when product has no categories")
        void shouldReturnProduct_whenProductHasNoCategories() throws Exception {
            // Given
            Product product = productRepository.save(Product.builder()
                .externalId("EXT-NO-CAT")
                .sku("SKU-NO-CAT")
                .name("Uncategorized Product")
                .storefrontId(activeStorefront.getId())
                .categoryIds(Set.of())
                .build());

            // When / Then
            mockMvc.perform(get(BASE_URL + "/{productId}", product.getId())
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(product.getId()))
                .andExpect(jsonPath("$.sku").value("SKU-NO-CAT"))
                .andExpect(jsonPath("$.categories.length()").value(0));
        }
    }

    @Nested
    @DisplayName("Product Retrieval Exception Scenarios")
    class ExceptionTests {

        @Test
        @DisplayName("Should return 404 Not Found when product ID does not exist")
        void shouldReturn404_whenProductDoesNotExist() throws Exception {
            Long nonExistentProductId = 999999L;

            mockMvc.perform(get(BASE_URL + "/{productId}", nonExistentProductId)
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 400 Bad Request when product ID type is invalid")
        void shouldReturn400_whenProductIdIsInvalidType() throws Exception {
            mockMvc.perform(get(BASE_URL + "/{productId}", "not-a-number")
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        }
    }

    private Category createCategory(String name) {
        Category category = new Category();
        category.setName(name);
        category.setStorefrontId(activeStorefront.getId());
        return categoryRepository.save(category);
    }
}
