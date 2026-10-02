package com.promox.core.promotion.internal.service;

import com.promox.core.product.ProductWithCategoriesDto;
import com.promox.core.promotion.internal.context.EngineWorkspace;
import com.promox.core.promotion.internal.dto.CreatePromotionRuleRequest;
import com.promox.core.promotion.internal.dto.PromotionFilterRequest;
import com.promox.core.promotion.internal.dto.UpdatePromotionRuleRequest;
import com.promox.core.promotion.internal.dto.UpdateUsageLimitsRequest;
import com.promox.core.promotion.internal.exception.PromotionBusinessException;
import com.promox.core.promotion.internal.persistence.PromotionRepository;
import com.promox.core.promotion.internal.persistence.PromotionRule;
import com.promox.core.promotion.internal.persistence.PromotionRuleBaseProjection;
import com.promox.core.promotion.internal.persistence.PromotionRuleSpecifications;
import com.promox.core.promotion.internal.persistence.PromotionRuleSummaryProjection;
import com.promox.core.promotion.internal.persistence.PromotionRuleUsage;
import com.promox.core.promotion.internal.service.engine.PromoRuleEngine;
import com.promox.core.promotion.event.PromotionRulesAppliedEvent;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class PromotionService {
    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of("createdAt", "updatedAt", "priority");

    private final PromotionRepository promotionRepository;
    private final PromotionRuleCacheReader promotionRuleCacheReader;
    private final PromotionRuleUsageService promotionRuleUsageService;
    private final PromoRuleEngine promoRuleEngine;
    private final ApplicationEventPublisher eventPublisher;

    public PromotionService(
        PromotionRepository promotionRepository,
        PromotionRuleCacheReader promotionRuleCacheReader,
        PromotionRuleUsageService promotionRuleUsageService,
        PromoRuleEngine promoRuleEngine,
        ApplicationEventPublisher eventPublisher
    ) {
        this.promotionRepository = promotionRepository;
        this.promotionRuleCacheReader = promotionRuleCacheReader;
        this.promotionRuleUsageService = promotionRuleUsageService;
        this.promoRuleEngine = promoRuleEngine;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
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

    @CacheEvict(cacheNames = PromotionRuleCacheReader.RULES_CACHE_NAME, key = "#storefrontId")
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

    @CacheEvict(cacheNames = PromotionRuleCacheReader.RULES_CACHE_NAME, key = "#promotionRule.storefrontId")
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

    // The cached projection carries hasUsageLimit, so enabling or disabling limits must evict it.
    @CacheEvict(cacheNames = PromotionRuleCacheReader.RULES_CACHE_NAME, key = "#promotionRule.storefrontId")
    @Transactional
    public void updateUsageLimits(
        UpdateUsageLimitsRequest request,
        PromotionRule promotionRule
    ) {
        promotionRule.setHasUsageLimit(request.hasUsageLimit());
        promotionRule.setUsageLimit(request.usageLimit());

        if (request.mutateRemainingUses()) {
            promotionRule.setRemainingUses(request.remainingUses());
        }

        promotionRuleUsageService.resetCounterAfterCommit(promotionRule.getId());
    }

    @Transactional
    public void reduceRemainingUses(Collection<Long> ruleIds) {
        if (ruleIds.isEmpty()) {
            return;
        }

        for (PromotionRule rule : promotionRepository.findAllById(ruleIds)) {
            if (rule.hasUsageLimit() && rule.getRemainingUses() > 0) {
                rule.setRemainingUses(rule.getRemainingUses() - 1);
            }
        }
    }

    /**
     * Gives one use back to each of the rules, never exceeding a rule's usage limit.
     */
    @Transactional
    public void restoreRemainingUses(Collection<Long> ruleIds) {
        if (ruleIds.isEmpty()) {
            return;
        }

        promotionRepository.restoreRemainingUses(ruleIds);
    }

    /**
     * Applies the storefront's active rules to the workspace and records the usage of the applied ones.
     *
     * @param products     the products of the order, recorded in the usage audit
     * @param reserveUsage whether uses of limited rules are reserved until confirmed, or counted as used right away
     * @return the recorded usage; empty when no rule was applied
     */
    @Transactional
    public Optional<PromotionRuleUsage> applyRules(
        Long storefrontId,
        EngineWorkspace workspace,
        List<ProductWithCategoriesDto> products,
        boolean reserveUsage
    ) {
        List<PromotionRuleBaseProjection> rules = promotionRuleCacheReader.findAllActiveSortedByPriority(storefrontId);

        promotionRuleUsageService.releaseAcquiredUsesOnRollback(workspace);
        promoRuleEngine.calculate(workspace, rules);

        if (workspace.getAppliedRules().isEmpty()) {
            return Optional.empty();
        }

        PromotionRuleUsage usage = promotionRuleUsageService.recordUsage(workspace, products, reserveUsage);

        eventPublisher.publishEvent(new PromotionRulesAppliedEvent(
            usage.getId(),
            storefrontId,
            List.copyOf(usage.getAppliedRuleIds())
        ));

        return Optional.of(usage);
    }

    @CacheEvict(cacheNames = PromotionRuleCacheReader.RULES_CACHE_NAME, allEntries = true)
    @Transactional
    public Optional<Long> deleteById(Long promotionRuleId) {
        int rowsDeleted = promotionRepository.deletePromotionRuleById(promotionRuleId);
        if (rowsDeleted == 0) {
            return Optional.empty();
        }

        promotionRuleUsageService.resetCounterAfterCommit(promotionRuleId);
        return Optional.of(promotionRuleId);
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
