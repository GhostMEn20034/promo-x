package com.promox.core.promotion.internal.service;

import com.promox.core.promotion.internal.dto.DefaultPromotionDto;
import com.promox.core.promotion.internal.PromotionMapper;
import com.promox.core.promotion.internal.dto.CreatePromotionRuleRequest;
import com.promox.core.promotion.internal.dto.UpdatePromotionRuleRequest;

import org.springframework.stereotype.Service;

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

    public DefaultPromotionDto createPromotionRule(Long storefrontId, CreatePromotionRuleRequest request) {
        Set<String> productExternalIds = Stream.concat(
            request.conditionTree().getProductIdentifiers().stream(),
            request.action().getProductIdentifiers().stream()
        ).collect(Collectors.toSet());

        promotionProductValidator.validateProductExternalIdsExistence(
            productExternalIds,
            storefrontId,
            null
        );
        return promotionMapper.toDto(promotionService.createPromotionRule(request, productExternalIds, storefrontId));
    }

    public Optional<DefaultPromotionDto> updatePromotionRule(
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
                promotionService.updatePromotionRule(request, productExternalIds, promotionRule);
                return promotionMapper.toDto(promotionRule);
            });
    }
}
