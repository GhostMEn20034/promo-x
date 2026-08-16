package com.promox.core.product.internal.service;

import com.promox.core.product.internal.dto.CreateProductRequest;
import com.promox.core.product.internal.exception.ProductBusinessException;
import com.promox.core.product.internal.persistence.Product;
import com.promox.core.product.internal.persistence.ProductRepository;

import jakarta.annotation.Nullable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
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
        CreateProductRequest creationRequest
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
    /*
    * <b>Preconditions:</b>
    * Assumes that all input data has already been pre-validated:
    * <ul>
    *   <li>External IDs are unique within the request batch.</li>
    *   <li>External IDs do not already exist in the database for the given storefront.</li>
    *   <li>All referenced category IDs exist and are valid for the given storefront.</li>
    * </ul>
    * */
    @Transactional
    public void createMultipleProducts(Long storefrontId, List<CreateProductRequest> items) {
        productRepository.saveAllInBatch(storefrontId, items);
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
                "EXTERNAL_ID_ALREADY_EXISTS",
                null,
                "Product with the specified external ID exists"
            );
        }
    }
}
