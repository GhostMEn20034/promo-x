package com.promox.core.product.internal.service;

import com.promox.core.category.CategoryDto;
import com.promox.core.category.CategoryLookupApi;
import com.promox.core.infrastructure.cache.BatchCacheManager;
import com.promox.core.product.*;
import com.promox.core.product.internal.ProductMapper;
import com.promox.core.product.internal.persistence.Product;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProductLookupApiImpl implements ProductLookupApi {
    // Must match the promox.cache.ttls key in application.yaml
    public static final String PRODUCTS_WITH_CATEGORIES_CACHE = "products-with-categories";

    // External IDs are only unique per storefront.
    public static String productCacheKey(Long storefrontId, String externalId) {
        return storefrontId + ":" + externalId;
    }

    private final ProductService productService;
    private final CategoryLookupApi categoryLookupApi;
    private final BatchCacheManager batchCacheManager;

    public ProductLookupApiImpl(
        ProductService productService,
        CategoryLookupApi categoryLookupApi,
        BatchCacheManager batchCacheManager
    ) {
        this.productService = productService;
        this.categoryLookupApi = categoryLookupApi;
        this.batchCacheManager = batchCacheManager;
    }

    @Override
    public Optional<ProductWithCategoriesDto> findWithCategoriesById(Long productId) {
        return productService.findById(productId)
            .map(product -> {
               List<CategoryDto> categories = product.getCategoryIds().isEmpty()
                   ? List.of() : categoryLookupApi.getListById(product.getCategoryIds(), product.getStorefrontId());

               return ProductMapper.toDto(product, categories);
            });
    }

    @Override
    public List<DefaultProductDto> findAllByExternalIds(Set<String> externalIds, Long storefrontId) {
        return productService.findAllByExternalIds(externalIds, storefrontId).stream()
            .map(ProductMapper::toDto)
            .toList();
    }

    @Override
    public List<ProductWithCategoriesDto> findAllWithCategoriesByExternalIds(Set<String> externalIds, Long storefrontId) {
        Map<String, ProductWithCategoriesDto> products = batchCacheManager.getAll(
            PRODUCTS_WITH_CATEGORIES_CACHE,
            externalIds,
            externalId -> productCacheKey(storefrontId, externalId),
            ProductWithCategoriesDto.class,
            missingExternalIds -> loadWithCategories(missingExternalIds, storefrontId)
        );

        return List.copyOf(products.values());
    }

    private Map<String, ProductWithCategoriesDto> loadWithCategories(Set<String> externalIds, Long storefrontId) {
        List<Product> products = productService.findAllByExternalIds(externalIds, storefrontId);
        if (products.isEmpty()) {
            return Map.of();
        }

        Set<Long> categoryIds = products.stream()
            .flatMap(product -> product.getCategoryIds().stream())
            .collect(Collectors.toSet());

        Map<Long, CategoryDto> categoriesById = categoryIds.isEmpty()
            ? Map.of()
            : categoryLookupApi.getListById(categoryIds, storefrontId).stream()
                .collect(Collectors.toMap(CategoryDto::id, Function.identity()));

        return products.stream()
            .collect(Collectors.toMap(
                Product::getExternalId,
                product -> {
                    List<CategoryDto> categories = product.getCategoryIds().stream()
                        .map(categoriesById::get)
                        .filter(Objects::nonNull)
                        .toList();

                    return ProductMapper.toDto(product, categories);
                }
            ));
    }

    @Override
    public ProductExternalIdsExistenceResult checkExistenceByExternalIdsAndStorefront(Set<String> productExternalIds, Long storefrontId) {
        return productService.checkExistenceByExternalIdsAndStorefront(productExternalIds, storefrontId);
    }

}
