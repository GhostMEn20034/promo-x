package com.promox.core.infrastructure.web;

import com.promox.core.shared.IntegrationTestBase;
import com.promox.core.storefront.internal.persistence.Storefront;
import com.promox.core.storefront.internal.persistence.StorefrontRepository;
import com.promox.core.testfixtures.security.WithMockAnonymousUser;
import com.promox.core.testfixtures.security.WithMockJwt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithMockJwt
public class ApiPathIntegrationTest extends IntegrationTestBase {

    @Autowired
    private StorefrontRepository storefrontRepository;

    @Test
    @DisplayName("Should serve the application's endpoints under /api")
    void controllers_ShouldBeServedUnderApiPrefix() throws Exception {
        Storefront storefront = storefrontRepository.save(new Storefront(null, "Tech Store", null));

        mockMvc.perform(get("/api/storefront/{storefrontId}", storefront.getId()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should not serve the application's endpoints without the /api prefix")
    void controllers_ShouldNotBeServedWithoutApiPrefix() throws Exception {
        Storefront storefront = storefrontRepository.save(new Storefront(null, "Tech Store", null));

        mockMvc.perform(get("/storefront/{storefrontId}", storefront.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockAnonymousUser
    @DisplayName("Should keep the health check at /actuator/health, without the prefix")
    void healthCheck_ShouldKeepItsPath() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
