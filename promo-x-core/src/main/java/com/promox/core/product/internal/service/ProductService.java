package com.promox.core.product.internal.service;

import com.promox.core.product.ProductExternalIdsExistenceResult;
import com.promox.core.product.event.ProductUpdatedEvent;
import com.promox.core.product.internal.dto.CreateProductRequest;
import com.promox.core.product.internal.dto.UpdateProductRequest;
import com.promox.core.product.internal.exception.ProductBusinessException;
import com.promox.core.product.internal.persistence.Product;
import com.promox.core.product.internal.persistence.ProductRepository;

import jakarta.annotation.Nullable;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;


@Service
public class ProductService {
    private final ApplicationEventPublisher eventPublisher;
    private final ProductRepository productRepository;

    public ProductService(
            ApplicationEventPublisher eventPublisher,
            ProductRepository productRepository
    ) {
        this.eventPublisher = eventPublisher;
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

    /**
     * Updates a managed product in place; changes are persisted through dirty checking.
     * <b>Precondition:</b> all referenced category IDs exist for the product's storefront.
     */
    @Transactional
    public void update(Product product, UpdateProductRequest request) {
        String previousExternalId = product.getExternalId();
        if (!previousExternalId.equals(request.externalId())) {
            validateExternalIdUniqueness(request.externalId(), product.getStorefrontId());
        }

        product.setExternalId(request.externalId());
        product.setSku(request.sku());
        product.setName(request.name());
        product.setCategoryIds(new HashSet<>(request.categoryIds()));

        // Flush so @UpdateTimestamp has set the new updatedAt before it goes into the event.
        productRepository.flush();

        eventPublisher.publishEvent(new ProductUpdatedEvent(
            product.getId(),
            previousExternalId,
            product.getExternalId(),
            product.getSku(),
            product.getName(),
            Set.copyOf(product.getCategoryIds()),
            product.getStorefrontId(),
            product.getUpdatedAt()
        ));
    }

    @Transactional(readOnly = true)
    public Optional<Product> findById(Long productId) {
        return productRepository.findWithCategoryById(productId);
    }


    @Transactional(readOnly = true)
    public List<Product> findAllByExternalIds(Collection<String> externalIds, Long storefrontId) {
        return productRepository.findAllByStorefrontIdAndExternalIdIn(storefrontId, externalIds);
    }

    @Transactional(readOnly = true)
    public List<String> findExternalIdsByCategoryId(Long categoryId, Long storefrontId) {
        return productRepository.findExternalIdsByStorefrontIdAndCategoryId(storefrontId, categoryId);
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

    public ProductExternalIdsExistenceResult checkExistenceByExternalIdsAndStorefront(
        Set<String> productExternalIds,
        Long storefrontId
    ) {
        if (productExternalIds.isEmpty()) {
            return new ProductExternalIdsExistenceResult(Set.of(), Set.of());
        }

        Set<String> existingIds = productRepository.findExistingExternalIds(productExternalIds, storefrontId);
        // Derive missing IDs via set difference
        Set<String> missingIds = new HashSet<>(productExternalIds);
        missingIds.removeAll(existingIds);

        return new ProductExternalIdsExistenceResult(existingIds, missingIds);
    }
}
