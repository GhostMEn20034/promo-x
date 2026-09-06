package com.promox.core.promotion.internal.service;

import com.promox.core.promotion.internal.dto.DefaultPromotionDto;
import com.promox.core.promotion.internal.PromotionMapper;
import com.promox.core.promotion.internal.dto.CreatePromotionRuleRequest;
import com.promox.core.promotion.internal.dto.UpdatePromotionRuleRequest;
import com.promox.core.promotion.internal.dto.UpdateUsageLimitsRequest;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Service
public class PromotionCommandHandler {
    private final PromotionMapper promotionMapper;
    private final PromotionService promotionService;
    private final PromotionProductValidator promotionProductValidator;

    public PromotionCommandHandler(PromotionMapper promotionMapper, PromotionService promotionService, PromotionProductValidator promotionProductValidator) {
        this.promotionMapper = promotionMapper;
        this.promotionService = promotionService;
        this.promotionProductValidator = promotionProductValidator;
    }

    @Transactional
    public DefaultPromotionDto create(Long storefrontId, CreatePromotionRuleRequest request) {
        Set<String> productExternalIds = Stream.concat(
            request.conditionTree().getProductIdentifiers().stream(),
            request.action().getProductIdentifiers().stream()
        ).collect(Collectors.toSet());

        promotionProductValidator.validateProductExternalIdsExistence(
            productExternalIds,
            storefrontId,
            null
        );
        return promotionMapper.toDto(promotionService.create(request, productExternalIds, storefrontId));
    }

    @Transactional
    public Optional<DefaultPromotionDto> updateById(
        Long promotionRuleId,
        UpdatePromotionRuleRequest request
    ) {
        return promotionService.findById(promotionRuleId)
            .map(promotionRule -> {
                Set<String> productExternalIds = Stream.concat(
                    request.conditionTree().getProductIdentifiers().stream(),
                    request.action().getProductIdentifiers().stream()
                ).collect(Collectors.toSet());

                promotionProductValidator.validateProductExternalIdsExistence(
                    productExternalIds,
                    promotionRule.getStorefrontId(),
                    null
                );
                promotionService.update(request, productExternalIds, promotionRule);
                return promotionMapper.toDto(promotionRule);
            });
    }

    @Transactional
    public Optional<DefaultPromotionDto> updateUsageLimitsById(
            Long promotionRuleId,
            UpdateUsageLimitsRequest request
    ) {
        return promotionService.findById(promotionRuleId)
                .map(promotionRule -> {
                    promotionService.updateUsageLimits(request, promotionRule);
                    return promotionMapper.toDto(promotionRule);
                });
    }

    @Transactional
    public Optional<Long> deleteById(Long promotionRuleId) {
        return promotionService.deleteById(promotionRuleId);
    }
}
