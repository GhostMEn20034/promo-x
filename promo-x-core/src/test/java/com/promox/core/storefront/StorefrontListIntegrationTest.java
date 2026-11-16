package com.promox.core.storefront;

import com.promox.core.shared.IntegrationTestBase;
import com.promox.core.storefront.internal.persistence.Storefront;
import com.promox.core.storefront.internal.persistence.StorefrontRepository;

import com.promox.core.testfixtures.security.WithMockAnonymousUser;
import com.promox.core.testfixtures.security.WithMockJwt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WithMockJwt
public class StorefrontListIntegrationTest extends IntegrationTestBase {
    private static final String LIST_URL = "/storefront/";

    @Autowired
    private StorefrontRepository storefrontRepository;

    @Test
    @DisplayName("Should return all storefronts sorted by ID with 200 OK")
    void getStorefronts_ShouldReturnAllStorefrontsSortedById() throws Exception {
        List<Storefront> saved = storefrontRepository.saveAll(List.of(
                new Storefront(null, "Tech Store", "https://techstore.com"),
                new Storefront(null, "Book Store", null)
        ));

        mockMvc.perform(get(LIST_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].id").value(saved.get(0).getId()))
                .andExpect(jsonPath("$.content[0].name").value("Tech Store"))
                .andExpect(jsonPath("$.content[0].website").value("https://techstore.com"))
                .andExpect(jsonPath("$.content[1].id").value(saved.get(1).getId()))
                .andExpect(jsonPath("$.content[1].name").value("Book Store"))
                .andExpect(jsonPath("$.content[1].website").value(nullValue()))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    @DisplayName("Should return an empty page when there are no storefronts")
    void getStorefronts_ShouldReturnEmptyPage_WhenNoStorefrontsExist() throws Exception {
        mockMvc.perform(get(LIST_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", empty()))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("Should return the requested page, with 1-based page numbers")
    void getStorefronts_ShouldReturnRequestedPage() throws Exception {
        storefrontRepository.saveAll(List.of(
                new Storefront(null, "Store A", null),
                new Storefront(null, "Store B", null),
                new Storefront(null, "Store C", null)
        ));

        mockMvc.perform(get(LIST_URL)
                        .param("page", "2")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("Store C"))
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    @DisplayName("Should keep sorting by ID when the client asks for another sort")
    void getStorefronts_ShouldIgnoreClientSort() throws Exception {
        storefrontRepository.saveAll(List.of(
                new Storefront(null, "Store A", null),
                new Storefront(null, "Store B", null)
        ));

        mockMvc.perform(get(LIST_URL).param("sort", "name,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name").value(contains("Store A", "Store B")));
    }

    @Test
    @DisplayName("Should ignore a sort by a property that doesn't exist")
    void getStorefronts_ShouldIgnoreUnknownSortProperty() throws Exception {
        storefrontRepository.save(new Storefront(null, "Tech Store", null));

        mockMvc.perform(get(LIST_URL).param("sort", "doesNotExist,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("Tech Store"));
    }

    @Test
    @WithMockAnonymousUser
    @DisplayName("Should return 401 Unauthorized when the caller is not authenticated")
    void getStorefronts_ShouldReturn401_WhenUserIsAnonymous() throws Exception {
        mockMvc.perform(get(LIST_URL))
                .andExpect(status().isUnauthorized());
    }
}
