package com.promox.core.storefront;

import com.promox.core.shared.IntegrationTestBase;
import com.promox.core.storefront.internal.dto.CreateStorefrontRequest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


public class StorefrontCreationIntegrationTest extends IntegrationTestBase {
    private static final String BASE_URL = "/storefront";
    private static final String STOREFRONT_CREATE_URL = BASE_URL + "/";

    @Autowired
    private ObjectMapper objectMapper;

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("provideValidStorefrontRequests")
    @DisplayName("Should create storefront successfully and return 201 Created for valid requests")
    void createStorefront_ShouldReturn201_WhenRequestIsValid(
            String testName,
            CreateStorefrontRequest request
    ) throws Exception {
        mockMvc.perform(post(STOREFRONT_CREATE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value(request.name()))
                .andExpect(jsonPath("$.website").value(request.website()));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("provideInvalidStorefrontRequests")
    @DisplayName("Should return 400 Bad Request when validation constraints are violated")
    void createStorefront_ShouldReturn400_WhenRequestIsInvalid(
            String testName,
            CreateStorefrontRequest request
    ) throws Exception {
        mockMvc.perform(post(STOREFRONT_CREATE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    private static Stream<Arguments> provideValidStorefrontRequests() {
        return Stream.of(
                Arguments.of(
                        "Standard store with name and website",
                        new CreateStorefrontRequest("Electronics Hub", "https://electronicshub.com")
                ),
                Arguments.of(
                        "Store with optional null website",
                        new CreateStorefrontRequest("Local Grocery", null)
                ),
                Arguments.of(
                        "Maximum allowed name length (50 chars)",
                        new CreateStorefrontRequest("A".repeat(50), "https://example.com")
                ),
                Arguments.of(
                        "Minimum allowed website length (1 char)",
                        new CreateStorefrontRequest("Minimalist Store", "a")
                ),
                Arguments.of(
                        "Maximum allowed website length (255 chars)",
                        new CreateStorefrontRequest("Max Web Store", "http://" + "a".repeat(240) + ".com")
                )
        );
    }

    private static Stream<Arguments> provideInvalidStorefrontRequests() {
        return Stream.of(
                Arguments.of(
                        "Blank name (empty string)",
                        new CreateStorefrontRequest("", "https://valid.com")
                ),
                Arguments.of(
                        "Blank name (only whitespace)",
                        new CreateStorefrontRequest("   ", "https://valid.com")
                ),
                Arguments.of(
                        "Null name",
                        new CreateStorefrontRequest(null, "https://valid.com")
                ),
                Arguments.of(
                        "Name exceeds 50 characters (51 chars)",
                        new CreateStorefrontRequest("A".repeat(51), "https://valid.com")
                ),
                Arguments.of(
                        "Website is empty string (violates min string size = 1)",
                        new CreateStorefrontRequest("Valid Name", "")
                ),
                Arguments.of(
                        "Website exceeds 255 characters (256 chars)",
                        new CreateStorefrontRequest("Valid Name", "a".repeat(256))
                )
        );
    }
}
