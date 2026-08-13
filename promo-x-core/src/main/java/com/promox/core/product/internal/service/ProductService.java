package com.promox.core.product.internal.service;

import com.promox.core.product.internal.dto.CreateSingleProductRequest;
import com.promox.core.product.internal.exception.ProductBusinessException;
import com.promox.core.product.internal.persistence.Product;
import com.promox.core.product.internal.persistence.ProductRepository;

import jakarta.annotation.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Optional;


@Service
public class ProductService {
    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public Product createSingleProduct(
        Long storefrontId,
        CreateSingleProductRequest creationRequest
    ) {
        validateExternalIdUniqueness(creationRequest.externalId(), storefrontId);

        var product = Product.builder()
            .externalId(creationRequest.externalId())
            .sku(creationRequest.sku())
            .name(creationRequest.name())
            .categoryIds(new HashSet<>(creationRequest.categoryIds()))
            .storefrontId(storefrontId)
            .build();

        return productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public Optional<Product> findById(Long productId) {
        return productRepository.findWithCategoryById(productId);
    }

    private void validateExternalIdUniqueness(@Nullable String externalId, Long storefrontId) {
        if (externalId == null) {
            return;
        }

        if (productRepository.existsByExternalIdAndStorefrontId(externalId, storefrontId)) {
            throw ProductBusinessException.of(
                "Validation failed: Product with the specified external ID exists",
                "PRODUCT_WITH_EXTERNAL_ID_EXISTS",
                null,
                "Product with the specified external ID exists"
            );
        }
    }
}
