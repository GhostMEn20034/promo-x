package com.promox.core.category;

import com.promox.core.category.internal.persistence.Category;
import com.promox.core.category.internal.persistence.CategoryRepository;
import com.promox.core.infrastructure.security.Roles;
import com.promox.core.shared.IntegrationTestBase;
import com.promox.core.storefront.internal.persistence.Storefront;
import com.promox.core.storefront.internal.persistence.StorefrontRepository;

import com.promox.core.testfixtures.security.WithMockJwt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WithMockJwt(roles = {Roles.EMPLOYEE})
public class CategoryRetrieveIntegrationTest extends IntegrationTestBase {
    private static final String BASE_URL = "/category";

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
    @DisplayName("Should return 200 OK with category details when root category exists")
    void getById_ShouldReturn200_WhenRootCategoryExists() throws Exception {
        Category rootCategory = categoryRepository.save(
                Category.builder()
                        .name("Electronics")
                        .externalId("EXT-ELEC")
                        .storefrontId(activeStorefront.getId())
                        .build()
        );

        mockMvc.perform(get(BASE_URL + "/{categoryId}", rootCategory.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(rootCategory.getId()))
                .andExpect(jsonPath("$.name").value("Electronics"))
                .andExpect(jsonPath("$.externalId").value("EXT-ELEC"))
                .andExpect(jsonPath("$.storefrontId").value(activeStorefront.getId()))
                .andExpect(jsonPath("$.parent").isEmpty());
    }

    @Test
    @DisplayName("Should return 200 OK with parent details when category has parent")
    void getById_ShouldReturn200_WhenCategoryWithParentExists() throws Exception {
        // Arrange
        Category parentCategory = categoryRepository.save(
                Category.builder()
                        .name("Electronics")
                        .externalId("EXT-ELEC")
                        .storefrontId(activeStorefront.getId())
                        .build()
        );

        Category childCategory = Category.builder()
                .name("Smartphones")
                .externalId("EXT-SMART")
                .storefrontId(activeStorefront.getId())
                .build();
        childCategory.setParent(parentCategory);

        Category savedChild = categoryRepository.save(childCategory);

        // Act & Assert
        mockMvc.perform(get(BASE_URL + "/{categoryId}", savedChild.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(savedChild.getId()))
                .andExpect(jsonPath("$.name").value("Smartphones"))
                .andExpect(jsonPath("$.externalId").value("EXT-SMART"))
                .andExpect(jsonPath("$.storefrontId").value(activeStorefront.getId()))
                .andExpect(jsonPath("$.parent.id").value(parentCategory.getId()))
                .andExpect(jsonPath("$.parent.name").value("Electronics"))
                .andExpect(jsonPath("$.parent.externalId").value("EXT-ELEC"));
    }

    @Test
    @DisplayName("Should return 404 Not Found when category does not exist")
    void getById_ShouldReturn404_WhenCategoryDoesNotExist() throws Exception {
        Long nonExistentId = 99999L;

        mockMvc.perform(get(BASE_URL + "/{categoryId}", nonExistentId))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockJwt(roles = Roles.EMPLOYEE)
    @DisplayName("Should return 200 OK when the user is an employee")
    void getById_ShouldReturn200_WhenUserIsEmployee() throws Exception {
        Category category = saveRootCategory();

        mockMvc.perform(get(BASE_URL + "/{categoryId}", category.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(category.getId()));
    }

    @Test
    @WithMockJwt(roles = Roles.AUDITOR)
    @DisplayName("Should return 200 OK when the user is an auditor")
    void getById_ShouldReturn200_WhenUserIsAuditor() throws Exception {
        Category category = saveRootCategory();

        mockMvc.perform(get(BASE_URL + "/{categoryId}", category.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(category.getId()));
    }

    private Category saveRootCategory() {
        return categoryRepository.save(
                Category.builder()
                        .name("Electronics")
                        .externalId("EXT-ELEC")
                        .storefrontId(activeStorefront.getId())
                        .build()
        );
    }
}
