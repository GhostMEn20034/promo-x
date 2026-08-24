package com.promox.core.product.internal.service;

import com.promox.core.category.CategoryDto;
import com.promox.core.category.CategoryLookupApi;
import com.promox.core.product.ProductExternalIdsExistenceResult;
import com.promox.core.product.ProductLookupApi;
import com.promox.core.product.ProductWithCategoriesDto;
import com.promox.core.product.internal.ProductMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class ProductLookupApiImpl implements ProductLookupApi {
    private final ProductService productService;
    private final CategoryLookupApi categoryLookupApi;

    public ProductLookupApiImpl(ProductService productService, CategoryLookupApi categoryLookupApi) {
        this.productService = productService;
        this.categoryLookupApi = categoryLookupApi;
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
    public ProductExternalIdsExistenceResult checkExistenceByExternalIdsAndStorefront(Set<String> productExternalIds, Long storefrontId) {
        return productService.checkExistenceByExternalIdsAndStorefront(productExternalIds, storefrontId);
    }

}
