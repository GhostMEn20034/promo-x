package com.promox.core.promotion.internal.service;

import com.promox.core.category.CategoryDto;
import com.promox.core.product.ProductWithCategoriesDto;
import com.promox.core.promotion.event.PromotionRuleUsageExpiredEvent;
import com.promox.core.promotion.internal.context.AppliedRuleLog;
import com.promox.core.promotion.internal.context.EngineWorkspace;
import com.promox.core.promotion.internal.context.MatchedRuleLog;
import com.promox.core.promotion.internal.exception.PromotionBusinessException;
import com.promox.core.promotion.internal.persistence.PromotionRepository;
import com.promox.core.promotion.internal.persistence.PromotionRule;
import com.promox.core.promotion.internal.persistence.PromotionRuleUsage;
import com.promox.core.promotion.internal.persistence.PromotionRuleUsageAudit;
import com.promox.core.promotion.internal.persistence.PromotionRuleUsageAuditProduct;
import com.promox.core.promotion.internal.persistence.PromotionRuleUsageAuditProductRepository;
import com.promox.core.promotion.internal.persistence.PromotionRuleUsageAuditRepository;
import com.promox.core.promotion.internal.persistence.PromotionRuleUsageCounterRepository;
import com.promox.core.promotion.internal.persistence.PromotionRuleUsageCounterRepository.Acquisition;
import com.promox.core.promotion.internal.persistence.PromotionRuleUsageRepository;
import com.promox.core.promotion.internal.persistence.PromotionRuleUsageStatus;
import com.promox.core.promotion.internal.service.engine.RuleUsageLimiter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;


@Service
public class PromotionRuleUsageService implements RuleUsageLimiter {
    private static final Logger log = LoggerFactory.getLogger(PromotionRuleUsageService.class);

    private final PromotionRuleUsageRepository usageRepository;
    private final PromotionRuleUsageAuditRepository auditRepository;
    private final PromotionRuleUsageAuditProductRepository auditProductRepository;
    private final PromotionRuleUsageCounterRepository counterRepository;
    private final PromotionRepository promotionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Duration reservationTtl;

    public PromotionRuleUsageService(
        PromotionRuleUsageRepository usageRepository,
        PromotionRuleUsageAuditRepository auditRepository,
        PromotionRuleUsageAuditProductRepository auditProductRepository,
        PromotionRuleUsageCounterRepository counterRepository,
        PromotionRepository promotionRepository,
        ApplicationEventPublisher eventPublisher,
        @Value("${promox.promotion.usage-reservation-ttl}") Duration reservationTtl
    ) {
        this.usageRepository = usageRepository;
        this.auditRepository = auditRepository;
        this.auditProductRepository = auditProductRepository;
        this.counterRepository = counterRepository;
        this.promotionRepository = promotionRepository;
        this.eventPublisher = eventPublisher;
        this.reservationTtl = reservationTtl;
    }

    /**
     * Takes one use from the rule's Redis counter. A missing counter (first use, or Redis lost its
     * data) is seeded from the rule's persisted remaining uses and then tried once more.
     */
    @Override
    public boolean tryAcquire(Long ruleId) {
        Acquisition acquisition = counterRepository.tryDecrement(ruleId);

        if (acquisition == Acquisition.MISSING) {
            Optional<Integer> remainingUses = promotionRepository.findRemainingUsesOfLimitedRule(ruleId);
            if (remainingUses.isEmpty()) {
                return false;
            }

            counterRepository.initializeIfAbsent(ruleId, remainingUses.get());
            acquisition = counterRepository.tryDecrement(ruleId);
        }

        return acquisition == Acquisition.ACQUIRED;
    }

    @Override
    public void release(Long ruleId) {
        counterRepository.increment(ruleId);
    }

    /**
     * Redis isn't part of the database transaction: if the transaction doesn't commit, the uses the
     * engine has taken for this workspace are given back. Must be called inside a transaction,
     * before the engine runs.
     */
    public void releaseAcquiredUsesOnRollback(EngineWorkspace workspace) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    workspace.getUsageLimitedAppliedRuleIds().forEach(counterRepository::increment);
                }
            }
        });
    }

    /**
     * Persists the usage of the rules applied in the workspace, together with its audit record.
     * <p>
     * The usage is {@code RESERVED} only when a reservation was requested and at least one applied
     * rule has a usage limit; with nothing to hold, it is {@code USED} straight away.
     *
     * @param products the products of the order, used to record their category external IDs
     */
    @Transactional
    public PromotionRuleUsage recordUsage(
        EngineWorkspace workspace,
        List<ProductWithCategoriesDto> products,
        boolean reserveUsage
    ) {
        List<Long> appliedRuleIds = workspace.getAppliedRules().stream()
            .map(AppliedRuleLog::ruleId)
            .toList();
        List<Long> limitedRuleIds = workspace.getUsageLimitedAppliedRuleIds();

        boolean reserved = reserveUsage && !limitedRuleIds.isEmpty();

        PromotionRuleUsage usage = usageRepository.save(
            PromotionRuleUsage.builder()
                .storefrontId(workspace.getStorefrontId())
                .status(reserved ? PromotionRuleUsageStatus.RESERVED : PromotionRuleUsageStatus.USED)
                .appliedRuleIds(appliedRuleIds)
                .limitedRuleIds(limitedRuleIds)
                .expiresAt(reserved ? Instant.now().plus(reservationTtl) : null)
                .build()
        );

        // Flushed so the audit row exists before its product rows, which reference it by plain ID.
        auditRepository.saveAndFlush(buildAudit(usage, workspace, appliedRuleIds));
        auditProductRepository.saveAll(buildAuditProducts(usage, workspace, products));

        return usage;
    }

    /**
     * @return empty when the usage doesn't exist
     * @throws PromotionBusinessException when the usage is not reserved
     */
    @Transactional
    public Optional<PromotionRuleUsage> confirm(UUID usageId) {
        return usageRepository.findWithLockById(usageId)
            .map(usage -> {
                requireReserved(usage, "confirmed");
                usage.setStatus(PromotionRuleUsageStatus.USED);
                return usage;
            });
    }

    /**
     * Cancels a reserved usage and gives the held uses back to its limited rules, both in the
     * database and (once the transaction has committed) in Redis.
     *
     * @return empty when the usage doesn't exist
     * @throws PromotionBusinessException when the usage is not reserved
     */
    @Transactional
    public Optional<PromotionRuleUsage> cancel(UUID usageId) {
        return usageRepository.findWithLockById(usageId)
            .map(usage -> {
                requireReserved(usage, "canceled");
                usage.setStatus(PromotionRuleUsageStatus.CANCELED);

                List<Long> limitedRuleIds = List.copyOf(usage.getLimitedRuleIds());
                if (!limitedRuleIds.isEmpty()) {
                    promotionRepository.restoreRemainingUses(limitedRuleIds);
                    afterCommit(() -> limitedRuleIds.forEach(counterRepository::increment));
                }

                return usage;
            });
    }

    /**
     * Expires up to {@code batchSize} reserved usages whose deadline has passed. For each one the
     * held uses are given back in Redis (once the transaction has committed) and a
     * {@link PromotionRuleUsageExpiredEvent} is published so they are given back in the database too.
     *
     * @return the number of usages expired; equal to {@code batchSize} when more may be waiting
     */
    @Transactional
    public int expireOverdueUsages(int batchSize) {
        Instant now = Instant.now();
        List<PromotionRuleUsage> overdueUsages = usageRepository.findOverdueWithLock(
            PromotionRuleUsageStatus.RESERVED,
            now,
            PageRequest.of(0, batchSize)
        );

        // With duplicates on purpose: a rule held by several expired usages gets a use back for each.
        List<Long> releasedRuleIds = new ArrayList<>();

        for (PromotionRuleUsage usage : overdueUsages) {
            usage.setStatus(PromotionRuleUsageStatus.EXPIRED);
            usage.setExpiredAt(now);

            List<Long> limitedRuleIds = List.copyOf(usage.getLimitedRuleIds());
            if (!limitedRuleIds.isEmpty()) {
                releasedRuleIds.addAll(limitedRuleIds);
                eventPublisher.publishEvent(new PromotionRuleUsageExpiredEvent(
                    usage.getId(),
                    usage.getStorefrontId(),
                    limitedRuleIds
                ));
            }
        }

        if (!releasedRuleIds.isEmpty()) {
            afterCommit(() -> releasedRuleIds.forEach(counterRepository::increment));
        }

        return overdueUsages.size();
    }

    /**
     * Drops the rule's Redis counter once the current transaction has committed, so the next use
     * seeds it again from the persisted remaining uses. For changes to a rule's usage limits.
     */
    public void resetCounterAfterCommit(Long ruleId) {
        afterCommit(() -> counterRepository.delete(ruleId));
    }

    private static void requireReserved(PromotionRuleUsage usage, String action) {
        if (usage.getStatus() == PromotionRuleUsageStatus.RESERVED) {
            return;
        }

        throw PromotionBusinessException.of(
            "Promotion rule usage cannot be %s".formatted(action),
            "PROMOTION_RULE_USAGE_NOT_RESERVED",
            null,
            "Only a reserved usage can be confirmed or canceled",
            Map.of("status", usage.getStatus().name())
        );
    }

    private PromotionRuleUsageAudit buildAudit(
        PromotionRuleUsage usage,
        EngineWorkspace workspace,
        List<Long> appliedRuleIds
    ) {
        List<Long> matchedRuleIds = workspace.getMatchedRules().stream()
            .map(MatchedRuleLog::ruleId)
            .toList();

        Set<String> promoCodes = workspace.getPromoCodes().stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        return PromotionRuleUsageAudit.builder()
            .usage(usage)
            .storefrontId(workspace.getStorefrontId())
            .sessionId(workspace.getSessionId())
            .customerId(workspace.getCustomerId())
            .promoCodes(promoCodes)
            .shippingCostBefore(workspace.getShippingCost())
            .shippingCostAfter(workspace.getNetShippingCost())
            .subtotalBefore(workspace.getItemsSubtotal())
            .subtotalAfter(workspace.getItemsNetSubtotal())
            .totalBefore(workspace.getItemsSubtotal().add(workspace.getShippingCost()))
            .totalAfter(workspace.getGrandTotal())
            .appliedRules(ruleReferences(appliedRuleIds))
            .matchedRules(ruleReferences(matchedRuleIds))
            .build();
    }

    // References, not loaded entities: only the IDs are needed to write the join rows.
    private Set<PromotionRule> ruleReferences(Collection<Long> ruleIds) {
        return ruleIds.stream()
            .map(promotionRepository::getReferenceById)
            .collect(Collectors.toSet());
    }

    private List<PromotionRuleUsageAuditProduct> buildAuditProducts(
        PromotionRuleUsage usage,
        EngineWorkspace workspace,
        List<ProductWithCategoriesDto> products
    ) {
        Map<String, List<String>> categoryExternalIdsByProduct = products.stream()
            .collect(Collectors.toMap(
                product -> product.base().externalId(),
                product -> product.categories().stream()
                    .map(CategoryDto::externalId)
                    .filter(Objects::nonNull)
                    .toList(),
                (existing, _) -> existing
            ));

        return workspace.getLineItems().stream()
            .map(item -> PromotionRuleUsageAuditProduct.builder()
                .ruleUsageAuditId(usage.getId())
                .storefrontId(workspace.getStorefrontId())
                .productExternalId(item.getProductExternalId())
                .productSku(item.getProductSku())
                .categories(categoryExternalIdsByProduct.getOrDefault(item.getProductExternalId(), List.of()))
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .build())
            .toList();
    }

    private static void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // The database change is already committed, so a Redis failure must not fail the request.
                try {
                    action.run();
                } catch (RuntimeException e) {
                    log.warn("Failed to update a promotion rule usage counter in Redis after commit", e);
                }
            }
        });
    }
}
