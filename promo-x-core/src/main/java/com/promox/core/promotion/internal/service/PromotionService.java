package com.promox.core.promotion.internal.service;

import com.promox.core.promotion.internal.dto.CreatePromotionRuleRequest;
import com.promox.core.promotion.internal.dto.PromotionFilterRequest;
import com.promox.core.promotion.internal.dto.UpdatePromotionRuleRequest;
import com.promox.core.promotion.internal.exception.PromotionBusinessException;
import com.promox.core.promotion.internal.persistence.PromotionRepository;
import com.promox.core.promotion.internal.persistence.PromotionRule;
import com.promox.core.promotion.internal.persistence.PromotionRuleSpecifications;
import com.promox.core.promotion.internal.persistence.PromotionRuleSummaryProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class PromotionService {
    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of("createdAt", "updatedAt", "priority");

    private final PromotionRepository promotionRepository;

    public PromotionService(PromotionRepository promotionRepository) {
        this.promotionRepository = promotionRepository;
    }

    public Page<PromotionRuleSummaryProjection> findAll(
        Long storefrontId,
        PromotionFilterRequest filter,
        Pageable pageable
    ) {
        validateSort(pageable.getSort());
        Specification<PromotionRule> spec = PromotionRuleSpecifications.withFilter(storefrontId, filter);

        return promotionRepository.findBy(spec, query -> query
            .as(PromotionRuleSummaryProjection.class)
            .page(pageable)
        );
    }

    public Optional<PromotionRule> findById(Long promotionRuleId) {
        return promotionRepository.findById(promotionRuleId);
    }

    @Transactional
    public PromotionRule create(
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
            .hasUsageLimit(request.hasUsageLimit())
            .usageLimit(request.usageLimit())
            .remainingUses(request.remainingUses())
            .conditionTree(request.conditionTree())
            .action(request.action())
            .productExternalIds(productExternalIds)
            .build();

        return promotionRepository.save(promotionRule);
    }

    @Transactional
    public void update(
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

    @Transactional
    public Optional<Long> deleteById(Long promotionRuleId) {
        int rowsDeleted = promotionRepository.deletePromotionRuleById(promotionRuleId);
        return rowsDeleted > 0 ? Optional.of(promotionRuleId) : Optional.empty();
    }

    private void validateSort(Sort sort) {
        for (Sort.Order order : sort) {
            if (!ALLOWED_SORT_PROPERTIES.contains(order.getProperty())) {
                throw PromotionBusinessException.of(
                    "Validation failed: invalid sort property",
                    "INVALID_SORT_PROPERTY",
                    null,
                    "Sorting by '" + order.getProperty() + "' is not allowed",
                    Map.of(
                        "invalidProperty", order.getProperty(),
                        "allowedProperties", ALLOWED_SORT_PROPERTIES
                    )
                );
            }
        }
    }
}
