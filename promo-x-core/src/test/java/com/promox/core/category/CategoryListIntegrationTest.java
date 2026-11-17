package com.promox.core.category;

import com.promox.core.category.internal.persistence.Category;
import com.promox.core.category.internal.persistence.CategoryRepository;
import com.promox.core.infrastructure.security.Roles;
import com.promox.core.shared.IntegrationTestBase;
import com.promox.core.storefront.internal.persistence.Storefront;
import com.promox.core.storefront.internal.persistence.StorefrontRepository;

import com.promox.core.testfixtures.security.WithMockAnonymousUser;
import com.promox.core.testfixtures.security.WithMockJwt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WithMockJwt(roles = {Roles.EMPLOYEE})
public class CategoryListIntegrationTest extends IntegrationTestBase {
    private static final String LIST_URL = "/api/category/";
    private static final String STOREFRONT_HEADER = "X-Storefront-Id";

    @Autowired
    private StorefrontRepository storefrontRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private Storefront activeStorefront;

    @BeforeEach
    void setUpStorefront() {
        activeStorefront = storefrontRepository.save(
                new Storefront(null, "Main Storefront", "https://mainstore.com")
        );
    }

    @Test
    @DisplayName("Should return the storefront's categories sorted by ID, with their parents")
    void getAll_ShouldReturnStorefrontCategoriesSortedById() throws Exception {
        Category electronics = saveCategory("Electronics", "EXT-ELEC", null);
        Category smartphones = saveCategory("Smartphones", "EXT-SMART", electronics);

        mockMvc.perform(get(LIST_URL).header(STOREFRONT_HEADER, activeStorefront.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].id").value(electronics.getId()))
                .andExpect(jsonPath("$.content[0].name").value("Electronics"))
                .andExpect(jsonPath("$.content[0].externalId").value("EXT-ELEC"))
                .andExpect(jsonPath("$.content[0].storefrontId").value(activeStorefront.getId()))
                .andExpect(jsonPath("$.content[0].parent").value(nullValue()))
                .andExpect(jsonPath("$.content[1].id").value(smartphones.getId()))
                .andExpect(jsonPath("$.content[1].name").value("Smartphones"))
                .andExpect(jsonPath("$.content[1].parent.id").value(electronics.getId()))
                .andExpect(jsonPath("$.content[1].parent.name").value("Electronics"))
                .andExpect(jsonPath("$.content[1].parent.externalId").value("EXT-ELEC"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    @DisplayName("Should not return categories of other storefronts")
    void getAll_ShouldNotReturnOtherStorefrontsCategories() throws Exception {
        saveCategory("Electronics", "EXT-ELEC", null);
        Storefront otherStorefront = storefrontRepository.save(new Storefront(null, "Other Storefront", null));
        categoryRepository.save(
                Category.builder()
                        .name("Books")
                        .externalId("EXT-BOOKS")
                        .storefrontId(otherStorefront.getId())
                        .build()
        );

        mockMvc.perform(get(LIST_URL).header(STOREFRONT_HEADER, activeStorefront.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name").value(contains("Electronics")))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("Should return an empty page when the storefront has no categories")
    void getAll_ShouldReturnEmptyPage_WhenStorefrontHasNoCategories() throws Exception {
        mockMvc.perform(get(LIST_URL).header(STOREFRONT_HEADER, activeStorefront.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", empty()))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("Should return the requested page, with 1-based page numbers")
    void getAll_ShouldReturnRequestedPage() throws Exception {
        saveCategory("Category A", "EXT-A", null);
        saveCategory("Category B", "EXT-B", null);
        saveCategory("Category C", "EXT-C", null);

        mockMvc.perform(get(LIST_URL)
                        .header(STOREFRONT_HEADER, activeStorefront.getId())
                        .param("page", "2")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("Category C"))
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    @DisplayName("Should keep sorting by ID when the client asks for another sort")
    void getAll_ShouldIgnoreClientSort() throws Exception {
        saveCategory("Category A", "EXT-A", null);
        saveCategory("Category B", "EXT-B", null);

        mockMvc.perform(get(LIST_URL)
                        .header(STOREFRONT_HEADER, activeStorefront.getId())
                        .param("sort", "name,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name").value(contains("Category A", "Category B")));
    }

    @Test
    @DisplayName("Should ignore a sort by a property that doesn't exist")
    void getAll_ShouldIgnoreUnknownSortProperty() throws Exception {
        saveCategory("Electronics", "EXT-ELEC", null);

        mockMvc.perform(get(LIST_URL)
                        .header(STOREFRONT_HEADER, activeStorefront.getId())
                        .param("sort", "doesNotExist,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("Electronics"));
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
        saveCategory("Electronics", "EXT-ELEC", null);

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

    private Category saveCategory(String name, String externalId, Category parent) {
        Category category = Category.builder()
                .name(name)
                .externalId(externalId)
                .storefrontId(activeStorefront.getId())
                .build();
        category.setParent(parent);
        return categoryRepository.save(category);
    }
}
