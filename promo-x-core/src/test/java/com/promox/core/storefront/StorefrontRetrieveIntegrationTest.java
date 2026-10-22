package com.promox.core.storefront;

import com.promox.core.shared.IntegrationTestBase;
import com.promox.core.storefront.internal.persistence.Storefront;
import com.promox.core.storefront.internal.persistence.StorefrontRepository;

import com.promox.core.testfixtures.security.WithMockAnonymousUser;
import com.promox.core.testfixtures.security.WithMockJwt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WithMockJwt
public class StorefrontRetrieveIntegrationTest extends IntegrationTestBase {
    private static final String BASE_URL = "/storefront";

    @Autowired
    private StorefrontRepository storefrontRepository;

    @Test
    @DisplayName("Should return storefront with 200 OK when storefront exists")
    void getStorefrontById_ShouldReturn200_WhenStorefrontExists() throws Exception {
        // Arrange
        Storefront savedStorefront = storefrontRepository.save(
                new Storefront(null, "Tech Store", "https://techstore.com")
        );

        // Act & Assert
        mockMvc.perform(get(BASE_URL + "/{storefrontId}", savedStorefront.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(savedStorefront.getId()))
                .andExpect(jsonPath("$.name").value("Tech Store"))
                .andExpect(jsonPath("$.website").value("https://techstore.com"));
    }

    @Test
    @DisplayName("Should return 404 Not Found when storefront does not exist")
    void getStorefrontById_ShouldReturn404_WhenStorefrontDoesNotExist() throws Exception {
        Long nonExistentId = 99999L;

        mockMvc.perform(get(BASE_URL + "/{storefrontId}", nonExistentId))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockAnonymousUser
    @DisplayName("Should return 401 Unauthorized when the caller is not authenticated")
    void getStorefrontById_ShouldReturn401_WhenUserIsAnonymous() throws Exception {
        Storefront savedStorefront = storefrontRepository.save(
                new Storefront(null, "Tech Store", "https://techstore.com")
        );

        mockMvc.perform(get(BASE_URL + "/{storefrontId}", savedStorefront.getId()))
                .andExpect(status().isUnauthorized());
    }
}
