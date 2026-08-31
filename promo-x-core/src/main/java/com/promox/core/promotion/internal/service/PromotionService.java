package com.promox.core.promotion.internal.service;

import com.promox.core.promotion.internal.dto.CreatePromotionRuleRequest;
import com.promox.core.promotion.internal.dto.UpdatePromotionRuleRequest;
import com.promox.core.promotion.internal.persistence.PromotionRepository;
import com.promox.core.promotion.internal.persistence.PromotionRule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Service
public class PromotionService {
    private final PromotionRepository promotionRepository;

    public PromotionService(PromotionRepository promotionRepository) {
        this.promotionRepository = promotionRepository;
    }

    public Optional<PromotionRule> findById(Long promotionRuleId) {
        return promotionRepository.findById(promotionRuleId);
    }

    @Transactional
    public PromotionRule createPromotionRule(
        CreatePromotionRuleRequest request,
        Set<String> productExternalIds,
        Long storefrontId
    ) {

        PromotionRule promotionRule = PromotionRule.builder()
            .storefrontId(storefrontId)
            .name(request.name())
            .priority(request.priority())
            .isShortCircuit(request.isShortCircuit())
            .isActive(request.isActive())
            .conditionTree(request.conditionTree())
            .action(request.action())
            .productExternalIds(productExternalIds)
            .build();

        return promotionRepository.save(promotionRule);
    }

    @Transactional
    public void updatePromotionRule(
        UpdatePromotionRuleRequest request,
        Set<String> productExternalIds,
        PromotionRule promotionRule
    ) {
        promotionRule.setName(request.name());
        promotionRule.setPriority(request.priority());
        promotionRule.setShortCircuit(request.isShortCircuit());
        promotionRule.setActive(request.isActive());
        promotionRule.setConditionTree(request.conditionTree());
        promotionRule.setAction(request.action());
        promotionRule.setProductExternalIds(new HashSet<>(productExternalIds));
    }
}
