package com.promox.core.storefront;

import com.promox.core.shared.IntegrationTestBase;
import com.promox.core.storefront.internal.dto.UpdateStorefrontRequest;
import com.promox.core.storefront.internal.persistence.Storefront;
import com.promox.core.storefront.internal.persistence.StorefrontRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


public class StorefrontUpdateIntegrationTest extends IntegrationTestBase {
    private static final String BASE_URL = "/storefront";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StorefrontRepository storefrontRepository;

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("provideValidUpdateRequests")
    @DisplayName("Should update storefront successfully and return 200 OK for valid requests")
    void updateStorefront_ShouldReturn200_WhenRequestIsValid(
            String testName,
            UpdateStorefrontRequest updateRequest
    ) throws Exception {
        // Arrange: Seed existing storefront into database
        Storefront existing = storefrontRepository.save(
                new Storefront(null, "Initial Store Name", "https://initial-website.com")
        );

        // Act & Assert
        mockMvc.perform(put(BASE_URL + "/{storefrontId}", existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(existing.getId()))
                .andExpect(jsonPath("$.name").value(updateRequest.name()))
                .andExpect(jsonPath("$.website").value(updateRequest.website()));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("provideInvalidUpdateRequests")
    @DisplayName("Should return 400 Bad Request with Problem Detail structure when validation fails")
    void updateStorefront_ShouldReturn400_WhenRequestIsInvalid(
            String testName,
            UpdateStorefrontRequest invalidRequest,
            String expectedErrorPath,
            String expectedErrorCode
    ) throws Exception {
        // Arrange: Seed existing storefront into database
        Storefront existing = storefrontRepository.save(
                new Storefront(null, "Initial Store Name", "https://initial-website.com")
        );

        // Act & Assert
        mockMvc.perform(put(BASE_URL + "/{storefrontId}", existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.instance").value(BASE_URL + "/" + existing.getId()))
                .andExpect(jsonPath("$.instance").value(BASE_URL + "/" + existing.getId()))
                // Checks if an error object exists in the array with both matching path AND code
                .andExpect(jsonPath("$.errors[?(@.path == '" + expectedErrorPath + "' && @.code == '" + expectedErrorCode + "')]").exists());
    }

    @Test
    @DisplayName("Should return 404 Not Found when attempting to update non-existent storefront")
    void updateStorefront_ShouldReturn404_WhenStorefrontDoesNotExist() throws Exception {
        Long nonExistentId = 99999L;
        UpdateStorefrontRequest request = new UpdateStorefrontRequest("Valid Name", "https://valid.com");

        mockMvc.perform(put(BASE_URL + "/{storefrontId}", nonExistentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    // --- Data Providers ---

    private static Stream<Arguments> provideValidUpdateRequests() {
        return Stream.of(
                Arguments.of(
                        "Update both name and website",
                        new UpdateStorefrontRequest("Updated Electronics", "https://new-electronics.com")
                ),
                Arguments.of(
                        "Update name and clear website (null)",
                        new UpdateStorefrontRequest("Store Without Website", null)
                ),
                Arguments.of(
                        "Maximum allowed name length (50 chars)",
                        new UpdateStorefrontRequest("N".repeat(50), "https://example.com")
                ),
                Arguments.of(
                        "Minimum allowed website length (1 char)",
                        new UpdateStorefrontRequest("Minimal Web Store", "a")
                ),
                Arguments.of(
                        "Maximum allowed website length (255 chars)",
                        new UpdateStorefrontRequest("Max Web Store", "https://" + "w".repeat(235) + ".com")
                )
        );
    }

    private static Stream<Arguments> provideInvalidUpdateRequests() {
        return Stream.of(
                Arguments.of(
                        "Blank name (empty string)",
                        new UpdateStorefrontRequest("", "https://valid.com"),
                        "name",
                        "NOT_BLANK"
                ),
                Arguments.of(
                        "Blank name (whitespace)",
                        new UpdateStorefrontRequest("   ", "https://valid.com"),
                        "name",
                        "NOT_BLANK"
                ),
                Arguments.of(
                        "Null name",
                        new UpdateStorefrontRequest(null, "https://valid.com"),
                        "name",
                        "NOT_BLANK"
                ),
                Arguments.of(
                        "Name exceeds 50 characters",
                        new UpdateStorefrontRequest("A".repeat(51), "https://valid.com"),
                        "name",
                        "SIZE"
                ),
                Arguments.of(
                        "Website empty string (violates @Size min = 1)",
                        new UpdateStorefrontRequest("Valid Name", ""),
                        "website",
                        "SIZE"
                ),
                Arguments.of(
                        "Website exceeds 255 characters",
                        new UpdateStorefrontRequest("Valid Name", "a".repeat(256)),
                        "website",
                        "SIZE"
                )
        );
    }
}
