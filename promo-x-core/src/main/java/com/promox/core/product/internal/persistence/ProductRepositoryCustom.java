package com.promox.core.product.internal.persistence;

import com.promox.core.product.internal.dto.CreateProductRequest;

import java.util.List;

public interface ProductRepositoryCustom {
    void saveAllInBatch(Long storefrontId, List<CreateProductRequest> items);
}
