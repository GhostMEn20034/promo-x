package com.promox.core.promotion.internal.service;

import com.promox.core.product.DefaultProductDto;
import com.promox.core.product.ProductLookupApi;
import com.promox.core.promotion.PromotionLookupApi;
import com.promox.core.promotion.internal.PromotionMapper;
import com.promox.core.promotion.internal.dto.DetailedPromotionDto;
import com.promox.core.promotion.internal.dto.PromotionFilterRequest;
import com.promox.core.promotion.internal.dto.PromotionSummaryDto;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;


@Service
public class PromotionLookupApiImpl implements PromotionLookupApi {
    private final PromotionService promotionService;
    private final PromotionMapper promotionMapper;
    private final ProductLookupApi productLookupApi;

    public PromotionLookupApiImpl(
        PromotionService promotionService,
        PromotionMapper promotionMapper,
        ProductLookupApi productLookupApi
    ) {
        this.promotionService = promotionService;
        this.promotionMapper = promotionMapper;
        this.productLookupApi = productLookupApi;
    }

    @Override
    public Optional<DetailedPromotionDto> findById(Long promotionId) {
        return promotionService.findWithProductsById(promotionId).map(promotion -> {
            List<DefaultProductDto> products = productLookupApi.findAllByExternalIds(
                promotion.getProductExternalIds(),
                promotion.getStorefrontId()
            );
            return promotionMapper.toDetailedDto(promotion, products);
        });
    }

    @Override
    public Page<PromotionSummaryDto> findAll(
        Long storefrontId,
        PromotionFilterRequest filter,
        Pageable pageable
    ) {
        return promotionService.findAll(storefrontId, filter, pageable)
            .map(promotionMapper::toSummaryDto);
    }
}
