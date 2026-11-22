package com.promox.core.promotion.internal.service;

import com.promox.core.promotion.internal.exception.PromotionBusinessException;
import com.promox.core.promotion.internal.persistence.PromotionRuleUsageAuditRepository;
import com.promox.core.promotion.internal.persistence.PromotionRuleUsageAuditRow;
import com.promox.core.promotion.internal.persistence.PromotionRuleUsageAuditRuleRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;


/**
 * Reads usage audits in separate queries: a page of audits with their usages' status first, then the rules of only
 * that page's usages, so the rules are never joined into the paged query.
 */
@Service
public class PromotionRuleUsageAuditService {
    private static final String SORT_PROPERTY = "createdAt";

    private final PromotionRuleUsageAuditRepository promotionRuleUsageAuditRepository;

    public PromotionRuleUsageAuditService(PromotionRuleUsageAuditRepository promotionRuleUsageAuditRepository) {
        this.promotionRuleUsageAuditRepository = promotionRuleUsageAuditRepository;
    }

    /**
     * A page of the storefront's audits, newest first unless sorted by {@code createdAt,asc}. A non-empty
     * {@code matchedRuleIds} keeps the audits that matched any of those rules, and {@code appliedRuleIds} those that
     * applied any of them; when both are given, an audit must pass both.
     *
     * @throws PromotionBusinessException {@code INVALID_SORT_PROPERTY} when sorted by anything but {@code createdAt}
     */
    public Page<PromotionRuleUsageAuditRow> getWithoutRulesByStorefrontId(
        Long storefrontId,
        Pageable pageable,
        Set<Long> matchedRuleIds,
        Set<Long> appliedRuleIds
    ) {
        boolean oldestFirst = isOldestFirst(pageable.getSort());
        Long[] matched = matchedRuleIds.toArray(Long[]::new);
        Long[] applied = appliedRuleIds.toArray(Long[]::new);

        List<PromotionRuleUsageAuditRow> rows = oldestFirst
            ? promotionRuleUsageAuditRepository.findAuditPageOldestFirst(
                storefrontId, matched, applied, pageable.getPageSize(), pageable.getOffset())
            : promotionRuleUsageAuditRepository.findAuditPageNewestFirst(
                storefrontId, matched, applied, pageable.getPageSize(), pageable.getOffset());

        // Counts only when the page doesn't already tell the total, e.g. a last page that isn't full
        return PageableExecutionUtils.getPage(
            rows,
            pageable,
            () -> promotionRuleUsageAuditRepository.countAudits(storefrontId, matched, applied)
        );
    }

    /** The rules each usage matched, from the highest priority down. Usages that matched none are left out. */
    public Map<UUID, List<PromotionRuleUsageAuditRuleRow>> getMatchedRules(Collection<UUID> usageIds) {
        return usageIds.isEmpty()
            ? Map.of()
            : byUsage(promotionRuleUsageAuditRepository.findMatchedRules(usageIds));
    }

    /** The rules each usage applied, in the order they were applied. Usages that applied none are left out. */
    public Map<UUID, List<PromotionRuleUsageAuditRuleRow>> getAppliedRules(Collection<UUID> usageIds) {
        return usageIds.isEmpty()
            ? Map.of()
            : byUsage(promotionRuleUsageAuditRepository.findAppliedRules(usageIds));
    }

    /** Groups the rows by usage, keeping the order the query returned them in. */
    private static Map<UUID, List<PromotionRuleUsageAuditRuleRow>> byUsage(List<PromotionRuleUsageAuditRuleRow> rows) {
        return rows.stream().collect(Collectors.groupingBy(
            PromotionRuleUsageAuditRuleRow::usageId,
            LinkedHashMap::new,
            Collectors.toList()
        ));
    }

    private static boolean isOldestFirst(Sort sort) {
        boolean oldestFirst = false;
        for (Sort.Order order : sort) {
            if (!SORT_PROPERTY.equals(order.getProperty())) {
                throw PromotionBusinessException.of(
                    "Validation failed: invalid sort property",
                    "INVALID_SORT_PROPERTY",
                    null,
                    "Sorting by '" + order.getProperty() + "' is not allowed",
                    Map.of(
                        "invalidProperty", order.getProperty(),
                        "allowedProperties", Set.of(SORT_PROPERTY)
                    )
                );
            }
            oldestFirst = order.isAscending();
        }
        return oldestFirst;
    }
}
