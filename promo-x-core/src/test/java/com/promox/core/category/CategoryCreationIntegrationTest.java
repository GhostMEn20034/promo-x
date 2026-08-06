package com.promox.core.category;

import com.promox.core.category.dto.CreateCategoryRequest;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


public class CategoryCreationIntegrationTest extends IntegrationTestBase {
    private static final String BASE_URL = "/category";
    private static final String STOREFRONT_HEADER = "X-Storefront-Id";

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
    @DisplayName("Should create root category successfully when request is valid")
    void createCategory_ShouldReturn201_WhenCreatingRootCategory() throws Exception {
        var request = new CreateCategoryRequest("Electronics", "EXT-ELEC-01", null);

        mockMvc.perform(post(BASE_URL + "/")
                        .header(STOREFRONT_HEADER, activeStorefront.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Electronics"))
                .andExpect(jsonPath("$.externalId").value("EXT-ELEC-01"))
                .andExpect(jsonPath("$.storefrontId").value(activeStorefront.getId()))
                .andExpect(jsonPath("$.parent").isEmpty());
    }

    @Test
    @DisplayName("Should create child category linked to existing parent in same storefront")
    void createCategory_ShouldReturn201_WhenCreatingChildCategory() throws Exception {
        Category parent = categoryRepository.save(
                Category.builder()
                        .name("Laptops")
                        .externalId("EXT-LAPTOPS")
                        .storefrontId(activeStorefront.getId())
                        .build()
        );

        var request = new CreateCategoryRequest("Gaming Laptops", "EXT-GAMING-LAPTOPS", parent.getId());

        mockMvc.perform(post(BASE_URL + "/")
                        .header(STOREFRONT_HEADER, activeStorefront.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Gaming Laptops"))
                .andExpect(jsonPath("$.storefrontId").value(activeStorefront.getId()))
                .andExpect(jsonPath("$.parent.id").value(parent.getId()))
                .andExpect(jsonPath("$.parent.name").value("Laptops"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("provideInvalidCategoryRequests")
    @DisplayName("Should return 400 Bad Request when DTO constraint validation fails")
    void createCategory_ShouldReturn400_WhenValidationFails(
            String testName,
            CreateCategoryRequest invalidRequest,
            String expectedErrorPath,
            String expectedErrorCode
    ) throws Exception {
        mockMvc.perform(post(BASE_URL + "/")
                        .header(STOREFRONT_HEADER, activeStorefront.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.path == '" + expectedErrorPath + "' && @.code == '" + expectedErrorCode + "')]").exists());
    }


    @Test
    @DisplayName("Should return 400 Bad Request when X-Storefront-Id header is missing")
    void createCategory_ShouldReturn400_WhenStorefrontHeaderIsMissing() throws Exception {
        var request = new CreateCategoryRequest("Books", null, null);

        mockMvc.perform(post(BASE_URL + "/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should throw business error when specified Storefront does not exist")
    void createCategory_ShouldReturnError_WhenStorefrontDoesNotExist() throws Exception {
        Long nonExistentStorefrontId = 99999L;
        var request = new CreateCategoryRequest("Smartphones", null, null);

        mockMvc.perform(post(BASE_URL + "/")
                        .header(STOREFRONT_HEADER, nonExistentStorefrontId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Should fail when externalId already exists for the SAME storefront")
    void createCategory_ShouldFail_WhenExternalIdIsNotUniqueWithinStorefront() throws Exception {
        // Seed initial category with externalId
        categoryRepository.save(
                Category.builder()
                        .name("Audio")
                        .externalId("SHARED-EXT-ID")
                        .storefrontId(activeStorefront.getId())
                        .build()
        );

        var duplicateRequest = new CreateCategoryRequest("Speakers", "SHARED-EXT-ID", null);

        mockMvc.perform(post(BASE_URL + "/")
                        .header(STOREFRONT_HEADER, activeStorefront.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateRequest)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Should allow same externalId across DIFFERENT storefronts")
    void createCategory_ShouldSucceed_WhenSameExternalIdUsedInDifferentStorefront() throws Exception {
        // Seed second storefront
        Storefront secondStorefront = storefrontRepository.save(
                new Storefront(null, "Second Storefront", "https://secondstore.com")
        );

        // Seed category in activeStorefront with externalId "GLOBAL-EXT-1"
        categoryRepository.save(
                Category.builder()
                        .name("Audio")
                        .externalId("GLOBAL-EXT-1")
                        .storefrontId(activeStorefront.getId())
                        .build()
        );

        // Try creating category in secondStorefront with SAME externalId
        var requestForSecondStore = new CreateCategoryRequest("Audio Devices", "GLOBAL-EXT-1", null);

        mockMvc.perform(post(BASE_URL + "/")
                        .header(STOREFRONT_HEADER, secondStorefront.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestForSecondStore)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.externalId").value("GLOBAL-EXT-1"))
                .andExpect(jsonPath("$.storefrontId").value(secondStorefront.getId()));
    }

    @Test
    @DisplayName("Should fail when parent category belongs to a DIFFERENT storefront")
    void createCategory_ShouldFail_WhenParentCategoryBelongsToOtherStorefront() throws Exception {
        // Seed second storefront & its parent category
        Storefront otherStorefront = storefrontRepository.save(
                new Storefront(null, "Other Storefront", "https://other.com")
        );
        Category otherStorefrontParent = categoryRepository.save(
                Category.builder()
                        .name("Foreign Category")
                        .externalId("FOREIGN-1")
                        .storefrontId(otherStorefront.getId())
                        .build()
        );

        // Try referencing foreign parent under activeStorefront
        var request = new CreateCategoryRequest("Invalid Child", null, otherStorefrontParent.getId());

        mockMvc.perform(post(BASE_URL + "/")
                        .header(STOREFRONT_HEADER, activeStorefront.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    private static Stream<Arguments> provideInvalidCategoryRequests() {
        return Stream.of(
                Arguments.of(
                        "Blank name (empty string)",
                        new CreateCategoryRequest("", "EXT-1", null),
                        "name",
                        "NOT_BLANK"
                ),
                Arguments.of(
                        "Name shorter than 2 characters",
                        new CreateCategoryRequest("A", "EXT-1", null),
                        "name",
                        "SIZE"
                ),
                Arguments.of(
                        "Name exceeds 50 characters",
                        new CreateCategoryRequest("C".repeat(51), "EXT-1", null),
                        "name",
                        "SIZE"
                ),
                Arguments.of(
                        "External ID is empty string (violates @Size min = 1)",
                        new CreateCategoryRequest("Valid Category", "", null),
                        "externalId",
                        "SIZE"
                ),
                Arguments.of(
                        "External ID exceeds 512 characters",
                        new CreateCategoryRequest("Valid Category", "E".repeat(513), null),
                        "externalId",
                        "SIZE"
                )
        );
    }
}
