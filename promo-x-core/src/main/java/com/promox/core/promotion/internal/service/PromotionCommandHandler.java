package com.promox.core.promotion.internal.service;

import com.promox.core.category.CategoryDto;
import com.promox.core.product.ProductWithCategoriesDto;
import com.promox.core.promotion.internal.context.EngineWorkspace;
import com.promox.core.promotion.internal.dto.*;
import com.promox.core.promotion.internal.PromotionMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Service
public class PromotionCommandHandler {
    private final PromotionMapper promotionMapper;
    private final PromotionService promotionService;
    private final PromotionProductValidator promotionProductValidator;

    public PromotionCommandHandler(
            PromotionMapper promotionMapper,
            PromotionService promotionService,
            PromotionProductValidator promotionProductValidator
    ) {
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

    @Transactional
    public PromotionEvaluationResponse evaluate(Long storefrontId, PromotionEvaluationRequest evaluationRequest) {
        Set<String> productExternalIds = evaluationRequest.items().stream()
                .map(PromotionEvaluationItem::productExternalId)
                .collect(Collectors.toSet());

        List<ProductWithCategoriesDto> products = promotionProductValidator.validateAndFetchOrderProducts(
                productExternalIds,
                storefrontId,
                "items"
        );

        EngineWorkspace workspace = buildWorkspace(storefrontId, evaluationRequest, products);
        promotionService.applyRules(storefrontId, workspace);

        return promotionMapper.toEvaluationResponse(workspace);
    }

    private EngineWorkspace buildWorkspace(
            Long storefrontId,
            PromotionEvaluationRequest evaluationRequest,
            List<ProductWithCategoriesDto> products
    ) {
        Map<String, ProductWithCategoriesDto> productsByExternalId = products.stream()
                .collect(Collectors.toMap(
                        product -> product.base().externalId(),
                        Function.identity(),
                        (existing, _) -> existing
                ));

        List<LineItemRequest> lineItems = evaluationRequest.items().stream()
                .map(item -> {
                    ProductWithCategoriesDto product = productsByExternalId.get(item.productExternalId());
                    List<String> categories = product.categories().stream()
                            .map(CategoryDto::name)
                            .toList();

                    return new LineItemRequest(
                            item.productExternalId(),
                            product.base().sku(),
                            categories,
                            List.of(),
                            item.quantity(),
                            item.unitPrice()
                    );
                })
                .toList();

        PromotionEvaluationParams request = new PromotionEvaluationParams(
                evaluationRequest.sessionId(),
                evaluationRequest.customerId(),
                evaluationRequest.promoCodes(),
                lineItems,
                evaluationRequest.shippingCost()
        );

        return new EngineWorkspace(storefrontId, request);
    }
}
