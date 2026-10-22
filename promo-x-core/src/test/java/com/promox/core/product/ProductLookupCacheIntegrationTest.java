package com.promox.core.product;

import com.promox.core.category.CategoryDto;
import com.promox.core.category.internal.dto.UpdateCategoryRequest;
import com.promox.core.category.internal.persistence.Category;
import com.promox.core.category.internal.persistence.CategoryRepository;
import com.promox.core.infrastructure.security.Roles;
import com.promox.core.product.internal.persistence.Product;
import com.promox.core.product.internal.persistence.ProductRepository;
import com.promox.core.product.internal.service.ProductLookupApiImpl;
import com.promox.core.shared.IntegrationTestBase;

import com.promox.core.testfixtures.security.WithMockJwt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.modulith.test.EnableScenarios;
import org.springframework.modulith.test.Scenario;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithMockJwt(roles = Roles.EMPLOYEE)
@EnableScenarios
public class ProductLookupCacheIntegrationTest extends IntegrationTestBase {
    private static final Long STOREFRONT_ID = 100L;
    private static final Long OTHER_STOREFRONT_ID = 200L;
    private static final String CACHE_KEY_PATTERN = ProductLookupApiImpl.PRODUCTS_WITH_CATEGORIES_CACHE + "::*";

    private static final String KEYBOARD = "PROD-KB-01";
    private static final String MOUSE = "PROD-MS-01";

    /** Well below the configured 1h TTL, so a rewrite of the entry would be visible. */
    private static final Duration SHORTENED_TTL = Duration.ofSeconds(100);

    @Autowired
    private ProductLookupApi productLookupApi;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private ObjectMapper objectMapper;

    private Category keyboards;

    @BeforeEach
    void setUp() {
        keyboards = saveCategory("CAT-KEYBOARDS", "Keyboards", null);
    }

    @Nested
    @DisplayName("Cache Miss Tests")
    class CacheMissTests {

        @Test
        @DisplayName("Should load missed products from the database and cache them with the configured TTL")
        void cacheMiss_loadsFromDatabaseAndCachesWithTtl() {
            saveProduct(STOREFRONT_ID, KEYBOARD, Set.of(keyboards.getId()));

            List<ProductWithCategoriesDto> products = lookup(STOREFRONT_ID, KEYBOARD);

            assertThat(externalIds(products)).containsExactly(KEYBOARD);
            assertThat(products.getFirst().categories()).extracting(CategoryDto::name).containsExactly("Keyboards");
            assertThat(ttlSeconds(STOREFRONT_ID, KEYBOARD)).isBetween(3500L, 3600L);
        }

        @Test
        @DisplayName("Should cache only the existing products when a lookup mixes existing and unknown external IDs")
        void mixedExistingAndUnknown_cachesOnlyExisting() {
            saveProduct(STOREFRONT_ID, KEYBOARD, Set.of());

            List<ProductWithCategoriesDto> products = lookup(STOREFRONT_ID, KEYBOARD, "NON-EXISTENT");

            assertThat(externalIds(products)).containsExactly(KEYBOARD);
            assertThat(isCached(STOREFRONT_ID, KEYBOARD)).isTrue();
            assertThat(isCached(STOREFRONT_ID, "NON-EXISTENT")).isFalse();
        }

        @Test
        @DisplayName("Should not cache anything when none of the external IDs exist")
        void unknownProducts_areNotCached() {
            List<ProductWithCategoriesDto> products = lookup(STOREFRONT_ID, "NON-EXISTENT");

            assertThat(products).isEmpty();
            assertThat(redisTemplate.keys(CACHE_KEY_PATTERN)).isEmpty();
        }

        @Test
        @DisplayName("Should return nothing and touch no cache entries for an empty set of external IDs")
        void emptyLookup_returnsEmptyWithoutCaching() {
            saveProduct(STOREFRONT_ID, KEYBOARD, Set.of());

            List<ProductWithCategoriesDto> products = productLookupApi.findAllWithCategoriesByExternalIds(
                Set.of(), STOREFRONT_ID
            );

            assertThat(products).isEmpty();
            assertThat(redisTemplate.keys(CACHE_KEY_PATTERN)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Cache Hit Tests")
    class CacheHitTests {

        @Test
        @DisplayName("Should serve cached products as typed values without hitting the database")
        void cacheHit_servesFromCacheWithoutDatabase() {
            saveProduct(STOREFRONT_ID, KEYBOARD, Set.of(keyboards.getId()));
            lookup(STOREFRONT_ID, KEYBOARD);

            productRepository.deleteAll();

            List<ProductWithCategoriesDto> products = lookup(STOREFRONT_ID, KEYBOARD);

            assertThat(products).hasSize(1);
            ProductWithCategoriesDto product = products.getFirst();
            assertThat(product.base().externalId()).isEqualTo(KEYBOARD);
            assertThat(product.base().sku()).isEqualTo(sku(STOREFRONT_ID, KEYBOARD));
            assertThat(product.base().storefrontId()).isEqualTo(STOREFRONT_ID);
            assertThat(product.categories()).extracting(CategoryDto::name).containsExactly("Keyboards");
        }

        @Test
        @DisplayName("Should restore nested category data, including the parent category, from the cache")
        void cacheHit_restoresNestedCategoryData() {
            Category peripherals = saveCategory("CAT-PERIPHERALS", "Peripherals", null);
            Category mechanical = saveCategory("CAT-MECHANICAL", "Mechanical Keyboards", peripherals);
            saveProduct(STOREFRONT_ID, KEYBOARD, Set.of(keyboards.getId(), mechanical.getId()));
            lookup(STOREFRONT_ID, KEYBOARD);

            productRepository.deleteAll();

            List<ProductWithCategoriesDto> products = lookup(STOREFRONT_ID, KEYBOARD);

            Set<CategoryDto> categories = products.getFirst().categories();
            assertThat(categories).extracting(CategoryDto::name)
                .containsExactlyInAnyOrder("Keyboards", "Mechanical Keyboards");

            CategoryDto cachedMechanical = categories.stream()
                .filter(category -> category.id().equals(mechanical.getId()))
                .findFirst()
                .orElseThrow();
            assertThat(cachedMechanical.externalId()).isEqualTo("CAT-MECHANICAL");
            assertThat(cachedMechanical.parent()).isNotNull();
            assertThat(cachedMechanical.parent().id()).isEqualTo(peripherals.getId());
            assertThat(cachedMechanical.parent().name()).isEqualTo("Peripherals");
        }

        @Test
        @DisplayName("Should not rewrite or refresh the TTL of an entry that was served from the cache")
        void cacheHit_doesNotRewriteEntry() {
            saveProduct(STOREFRONT_ID, KEYBOARD, Set.of());
            lookup(STOREFRONT_ID, KEYBOARD);
            redisTemplate.expire(cacheKey(STOREFRONT_ID, KEYBOARD), SHORTENED_TTL);

            lookup(STOREFRONT_ID, KEYBOARD);

            assertThat(ttlSeconds(STOREFRONT_ID, KEYBOARD)).isLessThanOrEqualTo(SHORTENED_TTL.toSeconds());
        }
    }

    @Nested
    @DisplayName("Partial Hit Tests")
    class PartialHitTests {

        @Test
        @DisplayName("Should load only the missed products from the database when some are already cached")
        void partialHit_loadsOnlyMisses() {
            saveProduct(STOREFRONT_ID, KEYBOARD, Set.of(keyboards.getId()));
            lookup(STOREFRONT_ID, KEYBOARD);

            // Gone from the database, so it can only come back from the cache.
            productRepository.deleteAll();
            saveProduct(STOREFRONT_ID, MOUSE, Set.of());

            List<ProductWithCategoriesDto> products = lookup(STOREFRONT_ID, KEYBOARD, MOUSE);

            assertThat(externalIds(products)).containsExactlyInAnyOrder(KEYBOARD, MOUSE);
        }

        @Test
        @DisplayName("Should write back only the missed products and leave already cached entries untouched")
        void partialHit_writesBackOnlyMisses() {
            saveProduct(STOREFRONT_ID, KEYBOARD, Set.of());
            saveProduct(STOREFRONT_ID, MOUSE, Set.of());
            lookup(STOREFRONT_ID, KEYBOARD);
            redisTemplate.expire(cacheKey(STOREFRONT_ID, KEYBOARD), SHORTENED_TTL);

            lookup(STOREFRONT_ID, KEYBOARD, MOUSE);

            assertThat(ttlSeconds(STOREFRONT_ID, KEYBOARD)).isLessThanOrEqualTo(SHORTENED_TTL.toSeconds());
            assertThat(ttlSeconds(STOREFRONT_ID, MOUSE)).isBetween(3500L, 3600L);
        }
    }

    @Nested
    @DisplayName("Key Isolation And Compatibility Tests")
    class KeyTests {

        @Test
        @DisplayName("Should cache the same external ID separately for each storefront")
        void sameExternalIdInDifferentStorefronts_isCachedPerStorefront() {
            saveProduct(STOREFRONT_ID, KEYBOARD, Set.of());
            saveProduct(OTHER_STOREFRONT_ID, KEYBOARD, Set.of());

            ProductWithCategoriesDto first = lookup(STOREFRONT_ID, KEYBOARD).getFirst();
            ProductWithCategoriesDto second = lookup(OTHER_STOREFRONT_ID, KEYBOARD).getFirst();

            assertThat(first.base().sku()).isEqualTo(sku(STOREFRONT_ID, KEYBOARD));
            assertThat(second.base().sku()).isEqualTo(sku(OTHER_STOREFRONT_ID, KEYBOARD));
            assertThat(isCached(STOREFRONT_ID, KEYBOARD)).isTrue();
            assertThat(isCached(OTHER_STOREFRONT_ID, KEYBOARD)).isTrue();
        }

        @Test
        @DisplayName("Should use the Spring Cache key format so entries can be evicted through the CacheManager")
        void cachedEntry_isEvictableThroughCacheManager() {
            saveProduct(STOREFRONT_ID, KEYBOARD, Set.of());
            lookup(STOREFRONT_ID, KEYBOARD);

            Objects.requireNonNull(cacheManager.getCache(ProductLookupApiImpl.PRODUCTS_WITH_CATEGORIES_CACHE))
                .evict(STOREFRONT_ID + ":" + KEYBOARD);
            productRepository.deleteAll();

            assertThat(isCached(STOREFRONT_ID, KEYBOARD)).isFalse();
            assertThat(lookup(STOREFRONT_ID, KEYBOARD)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Category Update Invalidation Tests")
    class CategoryUpdateInvalidationTests {

        @Test
        @DisplayName("Should evict only the cached products of the updated category within the event's storefront")
        void categoryUpdate_evictsOnlyProductsOfThatCategoryInStorefront(Scenario scenario) {
            saveProduct(STOREFRONT_ID, KEYBOARD, Set.of(keyboards.getId()));
            saveProduct(STOREFRONT_ID, MOUSE, Set.of());
            saveProduct(OTHER_STOREFRONT_ID, KEYBOARD, Set.of());
            lookup(STOREFRONT_ID, KEYBOARD, MOUSE);
            lookup(OTHER_STOREFRONT_ID, KEYBOARD);

            scenario.stimulate(() -> updateCategory(keyboards, "Mechanical Keyboards", "CAT-MECHANICAL"))
                .andWaitForStateChange(() -> isCached(STOREFRONT_ID, KEYBOARD), cached -> !cached)
                .andVerify(cached -> {
                    assertThat(isCached(STOREFRONT_ID, MOUSE)).isTrue();
                    assertThat(isCached(OTHER_STOREFRONT_ID, KEYBOARD)).isTrue();

                    CategoryDto category = lookup(STOREFRONT_ID, KEYBOARD).getFirst().categories().iterator().next();
                    assertThat(category.name()).isEqualTo("Mechanical Keyboards");
                    assertThat(category.externalId()).isEqualTo("CAT-MECHANICAL");
                });
        }
    }

    private void updateCategory(Category category, String name, String externalId) {
        try {
            mockMvc.perform(put("/category/{categoryId}", category.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new UpdateCategoryRequest(name, externalId, null))))
                .andExpect(status().isOk());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private List<ProductWithCategoriesDto> lookup(Long storefrontId, String... externalIds) {
        return productLookupApi.findAllWithCategoriesByExternalIds(Set.of(externalIds), storefrontId);
    }

    private Category saveCategory(String externalId, String name, Category parent) {
        return categoryRepository.save(
            Category.builder()
                .externalId(externalId)
                .name(name)
                .parent(parent)
                .storefrontId(STOREFRONT_ID)
                .build()
        );
    }

    private void saveProduct(Long storefrontId, String externalId, Set<Long> categoryIds) {
        productRepository.save(Product.builder()
            .storefrontId(storefrontId)
            .externalId(externalId)
            .sku(sku(storefrontId, externalId))
            .name("Product " + externalId)
            .categoryIds(new HashSet<>(categoryIds))
            .build());
    }

    private static String sku(Long storefrontId, String externalId) {
        return "SKU-" + storefrontId + "-" + externalId;
    }

    private static List<String> externalIds(List<ProductWithCategoriesDto> products) {
        return products.stream().map(product -> product.base().externalId()).toList();
    }

    private boolean isCached(Long storefrontId, String externalId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey(storefrontId, externalId)));
    }

    private Long ttlSeconds(Long storefrontId, String externalId) {
        return redisTemplate.getExpire(cacheKey(storefrontId, externalId));
    }

    private static String cacheKey(Long storefrontId, String externalId) {
        return ProductLookupApiImpl.PRODUCTS_WITH_CATEGORIES_CACHE + "::" + storefrontId + ":" + externalId;
    }
}
