package com.promox.core.category;

import com.promox.core.category.internal.dto.UpdateCategoryRequest;
import com.promox.core.category.internal.persistence.Category;
import com.promox.core.category.internal.persistence.CategoryRepository;
import com.promox.core.shared.IntegrationTestBase;
import com.promox.core.storefront.internal.persistence.Storefront;
import com.promox.core.storefront.internal.persistence.StorefrontRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


public class CategoryUpdateIntegrationTest extends IntegrationTestBase {
    private static final String BASE_URL = "/category";

    @Autowired
    private ObjectMapper objectMapper;

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
    @DisplayName("Should update category successfully when request is valid")
    void updateCategory_ShouldReturn200_WhenUpdatingCategoryNameAndExternalId() throws Exception {
        Category existing = categoryRepository.save(
                Category.builder()
                        .name("Old Name")
                        .externalId("OLD-EXT")
                        .storefrontId(activeStorefront.getId())
                        .build()
        );

        var updateRequest = new UpdateCategoryRequest("Updated Name", "NEW-EXT", null);

        mockMvc.perform(put(BASE_URL + "/{categoryId}", existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(existing.getId()))
                .andExpect(jsonPath("$.name").value("Updated Name"))
                .andExpect(jsonPath("$.externalId").value("NEW-EXT"))
                .andExpect(jsonPath("$.storefrontId").value(activeStorefront.getId()))
                .andExpect(jsonPath("$.parent").isEmpty());
    }

    @Test
    @DisplayName("Should assign parent category successfully when parent exists in same storefront")
    void updateCategory_ShouldReturn200_WhenAssigningValidParent() throws Exception {
        Category parent = categoryRepository.save(
                Category.builder()
                        .name("Parent Category")
                        .externalId("EXT-PARENT")
                        .storefrontId(activeStorefront.getId())
                        .build()
        );

        Category child = categoryRepository.save(
                Category.builder()
                        .name("Child Category")
                        .externalId("EXT-CHILD")
                        .storefrontId(activeStorefront.getId())
                        .build()
        );

        var updateRequest = new UpdateCategoryRequest("Child Category Updated", "EXT-CHILD-2", parent.getId());

        mockMvc.perform(put(BASE_URL + "/{categoryId}", child.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(child.getId()))
                .andExpect(jsonPath("$.parent.id").value(parent.getId()))
                .andExpect(jsonPath("$.parent.name").value("Parent Category"));
    }


    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("provideInvalidUpdateRequests")
    @DisplayName("Should return 400 Bad Request when request body fails DTO validation")
    void updateCategory_ShouldReturn400_WhenValidationFails(
            String testName,
            UpdateCategoryRequest invalidRequest,
            String expectedErrorPath,
            String expectedErrorCode
    ) throws Exception {
        Category existing = categoryRepository.save(
                Category.builder()
                        .name("Initial Category")
                        .externalId("EXT-INIT")
                        .storefrontId(activeStorefront.getId())
                        .build()
        );

        mockMvc.perform(put(BASE_URL + "/{categoryId}", existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.path == '" + expectedErrorPath + "' && @.code == '" + expectedErrorCode + "')]").exists());
    }


    @Test
    @DisplayName("Should return 404 Not Found when category to update does not exist")
    void updateCategory_ShouldReturn404_WhenCategoryDoesNotExist() throws Exception {
        Long nonExistentId = 99999L;
        var request = new UpdateCategoryRequest("Valid Name", "EXT-1", null);

        mockMvc.perform(put(BASE_URL + "/{categoryId}", nonExistentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should fail when updating externalId to an existing externalId in the same storefront")
    void updateCategory_ShouldFail_WhenExternalIdAlreadyExistsInSameStorefront() throws Exception {
        // First Category with TAKEN-EXT-ID
        categoryRepository.save(
                Category.builder()
                        .name("Category One")
                        .externalId("TAKEN-EXT-ID")
                        .storefrontId(activeStorefront.getId())
                        .build()
        );

        // Second Category
        Category category2 = categoryRepository.save(
                Category.builder()
                        .name("Category Two")
                        .externalId("FREE-EXT-ID")
                        .storefrontId(activeStorefront.getId())
                        .build()
        );

        // Try updating Category Two's externalId to TAKEN-EXT-ID
        var updateRequest = new UpdateCategoryRequest("Category Two Updated", "TAKEN-EXT-ID", null);

        mockMvc.perform(put(BASE_URL + "/{categoryId}", category2.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Should fail when assigned parent category belongs to a different storefront")
    void updateCategory_ShouldFail_WhenParentBelongsToDifferentStorefront() throws Exception {
        Storefront otherStorefront = storefrontRepository.save(
                new Storefront(null, "Other Storefront", "https://other.com")
        );

        Category foreignParent = categoryRepository.save(
                Category.builder()
                        .name("Foreign Parent")
                        .externalId("FOREIGN-EXT")
                        .storefrontId(otherStorefront.getId())
                        .build()
        );

        Category localCategory = categoryRepository.save(
                Category.builder()
                        .name("Local Category")
                        .externalId("LOCAL-EXT")
                        .storefrontId(activeStorefront.getId())
                        .build()
        );

        var updateRequest = new UpdateCategoryRequest("Local Updated", "LOCAL-EXT-2", foreignParent.getId());

        mockMvc.perform(put(BASE_URL + "/{categoryId}", localCategory.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isConflict());
    }


    private static Stream<Arguments> provideInvalidUpdateRequests() {
        return Stream.of(
                Arguments.of(
                        "Blank name (empty string)",
                        new UpdateCategoryRequest("", "EXT-1", null),
                        "name",
                        "NOT_BLANK"
                ),
                Arguments.of(
                        "Name shorter than 2 characters",
                        new UpdateCategoryRequest("A", "EXT-1", null),
                        "name",
                        "SIZE"
                ),
                Arguments.of(
                        "Name exceeds 50 characters",
                        new UpdateCategoryRequest("C".repeat(51), "EXT-1", null),
                        "name",
                        "SIZE"
                ),
                Arguments.of(
                        "External ID is empty string (violates @Size min = 1)",
                        new UpdateCategoryRequest("Valid Name", "", null),
                        "externalId",
                        "SIZE"
                ),
                Arguments.of(
                        "External ID exceeds 512 characters",
                        new UpdateCategoryRequest("Valid Name", "E".repeat(513), null),
                        "externalId",
                        "SIZE"
                )
        );
    }
}
