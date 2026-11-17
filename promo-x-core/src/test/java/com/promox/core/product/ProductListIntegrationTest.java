package com.promox.core.product;

import com.promox.core.category.internal.persistence.Category;
import com.promox.core.category.internal.persistence.CategoryRepository;
import com.promox.core.infrastructure.security.Roles;
import com.promox.core.product.internal.persistence.Product;
import com.promox.core.product.internal.persistence.ProductRepository;
import com.promox.core.shared.IntegrationTestBase;
import com.promox.core.storefront.internal.persistence.Storefront;
import com.promox.core.storefront.internal.persistence.StorefrontRepository;

import com.promox.core.testfixtures.security.WithMockAnonymousUser;
import com.promox.core.testfixtures.security.WithMockJwt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WithMockJwt(roles = {Roles.EMPLOYEE})
public class ProductListIntegrationTest extends IntegrationTestBase {
    private static final String LIST_URL = "/api/product/";
    private static final String STOREFRONT_HEADER = "X-Storefront-Id";

    @Autowired
    private StorefrontRepository storefrontRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Storefront activeStorefront;

    @BeforeEach
    void setUpStorefront() {
        activeStorefront = storefrontRepository.save(
                new Storefront(null, "Main Storefront", "https://mainstore.com")
        );
    }

    @Test
    @DisplayName("Should return the storefront's products newest first, with their categories")
    void getAll_ShouldReturnProductsNewestFirstWithCategories() throws Exception {
        Category electronics = saveCategory("Electronics", "CAT-ELEC");
        Category keyboards = saveCategory("Keyboards", "CAT-KB");
        Product keyboard = saveProduct("Mechanical keyboard", "PROD-KB", Set.of(electronics.getId(), keyboards.getId()));
        Product mouse = saveProduct("Mouse", "PROD-MS", Set.of());

        mockMvc.perform(get(LIST_URL).header(STOREFRONT_HEADER, activeStorefront.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].id").value(mouse.getId()))
                .andExpect(jsonPath("$.content[0].name").value("Mouse"))
                .andExpect(jsonPath("$.content[0].categories", empty()))
                .andExpect(jsonPath("$.content[1].id").value(keyboard.getId()))
                .andExpect(jsonPath("$.content[1].externalId").value("PROD-KB"))
                .andExpect(jsonPath("$.content[1].sku").value("SKU-PROD-KB"))
                .andExpect(jsonPath("$.content[1].name").value("Mechanical keyboard"))
                .andExpect(jsonPath("$.content[1].storefrontId").value(activeStorefront.getId()))
                .andExpect(jsonPath("$.content[1].createdAt").exists())
                .andExpect(jsonPath("$.content[1].updatedAt").exists())
                .andExpect(jsonPath("$.content[1].categories[*].name")
                        .value(containsInAnyOrder("Electronics", "Keyboards")))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    @DisplayName("Should sort by creation time, not by ID")
    void getAll_ShouldSortByCreationTimeNotId() throws Exception {
        Product olderId = saveProduct("Product A", "PROD-A", Set.of());
        saveProduct("Product B", "PROD-B", Set.of());
        // The product with the lower ID is made the newest one
        jdbcTemplate.update(
                "UPDATE product SET created_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().plus(1, ChronoUnit.DAYS)),
                olderId.getId()
        );

        mockMvc.perform(get(LIST_URL).header(STOREFRONT_HEADER, activeStorefront.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name").value(contains("Product A", "Product B")));
    }

    @Test
    @DisplayName("Should not return products of other storefronts")
    void getAll_ShouldNotReturnOtherStorefrontsProducts() throws Exception {
        saveProduct("Mouse", "PROD-MS", Set.of());
        Storefront otherStorefront = storefrontRepository.save(new Storefront(null, "Other Storefront", null));
        productRepository.save(Product.builder()
                .externalId("PROD-OTHER")
                .sku("SKU-OTHER")
                .name("Other product")
                .storefrontId(otherStorefront.getId())
                .categoryIds(new HashSet<>())
                .build());

        mockMvc.perform(get(LIST_URL).header(STOREFRONT_HEADER, activeStorefront.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name").value(contains("Mouse")))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("Should return an empty page when the storefront has no products")
    void getAll_ShouldReturnEmptyPage_WhenStorefrontHasNoProducts() throws Exception {
        mockMvc.perform(get(LIST_URL).header(STOREFRONT_HEADER, activeStorefront.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", empty()))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("Should return the requested page, with 1-based page numbers")
    void getAll_ShouldReturnRequestedPage() throws Exception {
        saveProduct("Product A", "PROD-A", Set.of());
        saveProduct("Product B", "PROD-B", Set.of());
        saveProduct("Product C", "PROD-C", Set.of());

        // Newest first: C, B on page 1 and A, the oldest, on page 2
        mockMvc.perform(get(LIST_URL)
                        .header(STOREFRONT_HEADER, activeStorefront.getId())
                        .param("page", "2")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("Product A"))
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    @DisplayName("Should keep sorting newest first when the client asks for another sort")
    void getAll_ShouldIgnoreClientSort() throws Exception {
        saveProduct("Product A", "PROD-A", Set.of());
        saveProduct("Product B", "PROD-B", Set.of());

        mockMvc.perform(get(LIST_URL)
                        .header(STOREFRONT_HEADER, activeStorefront.getId())
                        .param("sort", "name,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name").value(contains("Product B", "Product A")));
    }

    @Test
    @DisplayName("Should ignore a sort by a property that doesn't exist")
    void getAll_ShouldIgnoreUnknownSortProperty() throws Exception {
        saveProduct("Mouse", "PROD-MS", Set.of());

        mockMvc.perform(get(LIST_URL)
                        .header(STOREFRONT_HEADER, activeStorefront.getId())
                        .param("sort", "doesNotExist,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("Mouse"));
    }

    @Test
    @DisplayName("Should return 400 Bad Request when the X-Storefront-Id header is missing")
    void getAll_ShouldReturn400_WhenStorefrontHeaderIsMissing() throws Exception {
        mockMvc.perform(get(LIST_URL))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockJwt(roles = Roles.AUDITOR)
    @DisplayName("Should return 200 OK when the user is an auditor")
    void getAll_ShouldReturn200_WhenUserIsAuditor() throws Exception {
        saveProduct("Mouse", "PROD-MS", Set.of());

        mockMvc.perform(get(LIST_URL).header(STOREFRONT_HEADER, activeStorefront.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    @WithMockJwt(roles = Roles.PROMOTION_MANAGER)
    @DisplayName("Should return 403 Forbidden when the user is neither an employee nor an auditor")
    void getAll_ShouldReturn403_WhenUserLacksRole() throws Exception {
        mockMvc.perform(get(LIST_URL).header(STOREFRONT_HEADER, activeStorefront.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockAnonymousUser
    @DisplayName("Should return 401 Unauthorized when the caller is not authenticated")
    void getAll_ShouldReturn401_WhenUserIsAnonymous() throws Exception {
        mockMvc.perform(get(LIST_URL).header(STOREFRONT_HEADER, activeStorefront.getId()))
                .andExpect(status().isUnauthorized());
    }

    private Category saveCategory(String name, String externalId) {
        return categoryRepository.save(
                Category.builder()
                        .name(name)
                        .externalId(externalId)
                        .storefrontId(activeStorefront.getId())
                        .build()
        );
    }

    private Product saveProduct(String name, String externalId, Set<Long> categoryIds) {
        return productRepository.save(Product.builder()
                .externalId(externalId)
                .sku("SKU-" + externalId)
                .name(name)
                .storefrontId(activeStorefront.getId())
                .categoryIds(new HashSet<>(categoryIds))
                .build());
    }
}
